package edu.wisc.cs.sdn.vnet.rt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * Route table for a router.
 * @author Aaron Gember-Jacobson
 */
public class RipProtocol implements Runnable 
{
	
	public static int MULTICAST_RIP_IP = IPv4.toIPv4Address("224.0.0.9");
	public static MACAddress BROADCAST_MAC = MACAddress.valueOf("FF:FF:FF:FF:FF:FF");
	public static final Object RIP_ENTRIES_LOCK = new Object();//use this lock whenever you access and modify the entries
	
	private List<RIPv2Entry> entries;
	private Router rt;
		
	public RipProtocol() {
		entries = new ArrayList<RIPv2Entry>();
		rt = null;
	}
	
	public RipProtocol(Device rt) {
		entries = new ArrayList<RIPv2Entry>();
		this.rt = (Router) rt;
	}
	
	@Override
	public void run() {
		startRip();
	}
	
	/**
	 * Print content of the rip entries for debugging purposes
	 */
	public void print() {
		synchronized (RIP_ENTRIES_LOCK) {
			System.out.println("****RIP entries****");
			int i = 1;
			for (RIPv2Entry entry: this.entries) {
				System.out.format("Entry #%d\n", i);
				System.out.format("Address:  %s\n", IPv4.fromIPv4Address(entry.getAddress()));
				System.out.format("Subnet Mask:  %s\n", IPv4.fromIPv4Address(entry.getSubnetMask()));
				System.out.format("Next hop:  %s\n", IPv4.fromIPv4Address(entry.getNextHopAddress()));
				System.out.format("metric:  %d\n", entry.getMetric());
				System.out.format("ttl:  %d\n", entry.getTtl());
				i++;
			}
		}
	}
	
	/**
	 * Rip request is encapsulated in various other structures in the following order: rip->UDP->IPv4->Ethernet
	 * @param sourceIface
	 * @param destinationMac
	 * @param destinationIp
	 * @param commandType
	 * @return
	 */
    public static Ethernet createRipPacket(Iface sourceIface, MACAddress destinationMac,
    		int destinationIp, byte commandType, List<RIPv2Entry> entries){
        Ethernet packet = new Ethernet();
        packet.setSourceMACAddress(sourceIface.getMacAddress().toBytes());
        packet.setDestinationMACAddress(destinationMac.toBytes());
        packet.setEtherType(Ethernet.TYPE_IPv4);
        
        IPv4 ipPacket = new IPv4();
        ipPacket.setProtocol(IPv4.PROTOCOL_UDP);
        ipPacket.setSourceAddress(sourceIface.getIpAddress());
        ipPacket.setDestinationAddress(destinationIp);
        packet.setPayload(ipPacket);
        ipPacket.setParent(packet);
        
        UDP udpPacket = new UDP();
        udpPacket.setSourcePort(UDP.RIP_PORT);
        udpPacket.setDestinationPort(UDP.RIP_PORT);
        udpPacket.setParent(ipPacket);
        ipPacket.setPayload(udpPacket);
 
        RIPv2 ripRequest = new RIPv2();
        ripRequest.setParent(udpPacket);
        udpPacket.setPayload(ripRequest);
        
        ripRequest.setCommand(commandType);
        ripRequest.setEntries(entries);
        
        return packet;
    } 
    
    /**
     * Creates a deep clone of the rip entries list
     * @return the cloned list
     */
    public ArrayList<RIPv2Entry> getRIPTableCopy(){
    	ArrayList<RIPv2Entry> clone = new ArrayList<RIPv2Entry>();
    	synchronized (RIP_ENTRIES_LOCK) {
    		for (RIPv2Entry entry: entries) {
    			clone.add(new RIPv2Entry(entry));
    		}
    	}
    	return clone;
    }
    
    /**
     * Adds a RIP entry to the table 
     * @param r the RIP entry to be added
     * @return true if entry added/changed(besides ttl update), false if entry with better metric already exists or only ttl update is done
     */
    public boolean addRIPEntry(RIPv2Entry r, int sourceIp) {
    	synchronized (RIP_ENTRIES_LOCK) {
    		
    		for (RIPv2Entry currEntry: entries) {
    			
    			// find entry with the same destination address
    			if(currEntry.getAddress() == r.getAddress() &&
    					currEntry.getSubnetMask() == r.getSubnetMask()) {
    				
    					if(r.getMetric() < currEntry.getMetric() && r.getMetric() > 0) {
    						// Better metric so it replaces the old entry
    						entries.remove(currEntry);
    						entries.add(r);
    						return true;
    					} else if(r.getMetric() == currEntry.getMetric() && currEntry.getNextHopAddress() == sourceIp) {
    						//reset ttl of the entry
    						currEntry.resetTtl();
    						return false;
    					}
    					else {
    						return false;//nothing is updated
    					}
    					
    			} 
    			
    		}
    		// No existing entry found
    		entries.add(r);
    		return true;
    	}
    }
    
    /**
     * Start rip protocol. Send initial RIP requests and send unsolicited RIP response every 10 seconds
     */
    public void startRip(){
    	Collection<Iface> interfaces = rt.getInterfaces().values();
    	
    	for (Iface iface : interfaces) {
    		// add metric info of all interfaces of this router to the rip entries and router table
    		RIPv2Entry entry = new RIPv2Entry(iface.getIpAddress(), iface.getSubnetMask(), 0, 0);
    		this.addRIPEntry(entry, 0);
    	}
    	
    	for (Iface iface : interfaces) {
    		// send RIP request to all interfaces
    		rt.sendPacket(createRipPacket(iface, BROADCAST_MAC, MULTICAST_RIP_IP,
    				RIPv2.COMMAND_REQUEST, this.getRIPTableCopy()), iface);
    	}
    	
        while(true){
        	try{
        		Thread.sleep(10000);// wait for 10 seconds
        	} catch(Exception e) {}
            // check and update route entries. Expire outdated route entries(30s)
    		
        	synchronized (RIP_ENTRIES_LOCK) {
        		
        		ArrayList<RIPv2Entry> entriesCopy = new ArrayList<RIPv2Entry>(entries);// entries are not deep copied
        		for (RIPv2Entry entry: entriesCopy) {
        			if (entry.decreaseTtl((short)10) <= 0) {
        				// delete the entry
        				entries.remove(entry);
        				rt.getRouteTable().remove(entry.getAddress(), entry.getSubnetMask());
        			}
        			
        		}
        	}
        	
    		for (Iface iface : interfaces) {
    			// send unsolicited RIP response to all interfaces
    			Ethernet packet = 
    					createRipPacket(iface, BROADCAST_MAC, MULTICAST_RIP_IP,
    							RIPv2.COMMAND_RESPONSE, this.getRIPTableCopy());
        		rt.sendPacket(packet, iface);
        	}
        
			//TODO test
			this.print();
        }  
    }
}
