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
	 * Rip request is encapsulated in various other structures in the following order: rip->UDP->IPv4->Ethernet
	 * @param sourceIface
	 * @param destinationMac
	 * @param destinationIp
	 * @param commandType
	 * @return
	 */
    public static Ethernet createRipPacket(Iface sourceIface, MACAddress destinationMac,
    		int destinationIp, byte commandType){
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
        
        return packet;
    } 

    /**
     * Creates a clone of the rip entries list
     * @return the cloned list
     */
    public ArrayList<RIPv2Entry> getRIPTable(){
    	synchronized (RIP_ENTRIES_LOCK) {
    		return new ArrayList<RIPv2Entry>(entries);
    	}
    }
    
    /**
     * Adds a RIP entry to the table
     * @param r the RIP entry to be added
     */
    public void addRIPEntry(RIPv2Entry r) {
    	//TODO More robust contains check
    	//TODO Add to route table?
    	synchronized (RIP_ENTRIES_LOCK) {
    		if(entries.contains(r)) {
    			int rIndex = entries.indexOf(r);
    			entries.get(rIndex).resetTtl();
    		} else {
    			r.resetTtl();
    			entries.add(r);
    		}
    		
    	}
    }
    
    /**
     * Start rip protocol. Send initial RIP requests and send unsolicited RIP response every 10 seconds
     */
    public void startRip(){
    	Collection<Iface> interfaces = rt.getInterfaces().values();
    	for (Iface iface : interfaces) {
    		// send RIP request to all interfaces
    		rt.sendPacket(createRipPacket(iface, BROADCAST_MAC, MULTICAST_RIP_IP, RIPv2.COMMAND_REQUEST), iface);
    	}
        while(true){
        	try{
        		Thread.sleep(10000);// wait for 10 seconds
        	} catch(Exception e) {}
            // check and update route entries. Expire outdated route entries(30s)
        	synchronized (RIP_ENTRIES_LOCK) {
        		
        		for (RIPv2Entry entry: entries) {
        			
        			if (entry.decreaseTtl((short)10) <= 0) {
        				// delete the entry
        				entries.remove(entry);
        				rt.getRouteTable().remove(entry.getAddress(), entry.getSubnetMask());
        			}
        			
        		}
        		
        		for (Iface iface : interfaces) {
        			// send unsolicited RIP response to all interfaces
        			Ethernet packet = createRipPacket(iface, BROADCAST_MAC, MULTICAST_RIP_IP, RIPv2.COMMAND_RESPONSE);
        			RIPv2 ripPacket = (RIPv2) packet.getPayload().getPayload().getPayload();
        			ArrayList<RIPv2Entry> cloneList = new ArrayList<RIPv2Entry>();
        			for (RIPv2Entry i: entries) {
        				cloneList.add(i);
        			}
        			ripPacket.setEntries(cloneList);
            		rt.sendPacket(packet, iface);
            	}
        		
        	}
        }  
    }
}
