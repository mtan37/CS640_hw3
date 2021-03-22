package edu.wisc.cs.sdn.vnet.rt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	
    private Ethernet createRipPacket(Iface sourceIface, MACAddress destinationMac,
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
     * Start RIP v2 protocol
     */
    public void startRip(){
    	Collection<Iface> interfaces = rt.getInterfaces().values();
    	for (Iface iface : interfaces) {
    		//send RIP request to all interfaces
    		rt.sendPacket(createRipPacket(iface, BROADCAST_MAC, MULTICAST_RIP_IP, RIPv2.COMMAND_REQUEST), iface);
    	}
        while(true){
        	try{
        		Thread.sleep(10000);// wait for 10 seconds
        	} catch(Exception e) {}
            //check and update route entries. Expire outdated route entries(30s)
        	synchronized (RIP_ENTRIES_LOCK) {
        		for (RIPv2Entry entry: entries) {
        			
        			if (entry.decreaseTtl((short)10) <= 0) {
        				//TODO delete the entry
        			}
        			
        		}
            //TODO send unsolicited RIP response to all interfaces
        	}
        }  
    }
}
