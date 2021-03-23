package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;

	/** ARP cache for the router */
	private ArpCache arpCache;
	
	private RipProtocol ripP;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}

	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }

	public void setRipProtocl(RipProtocol ripP) {
		this.ripP = ripP;
	}
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}

		System.out.println("Loaded route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}

	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}

		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/

		switch(etherPacket.getEtherType())
		{
		case Ethernet.TYPE_IPv4:
			this.handleIpPacket(etherPacket, inIface);
			break;
        }

		/********************************************************************/
	}
    
	private void handleRipRequest(RIPv2 ripPacketPv2, Iface inIface, int sourceIp) {
		System.out.println("****Handle RIP Request****");
		// Respond with RIP response packet
		Ethernet packet = RipProtocol.createRipPacket(inIface, inIface.getMacAddress(),
				sourceIp, RIPv2.COMMAND_RESPONSE, ripP.getRIPTableCopy());
		this.sendPacket(packet, inIface);
	}
	
	private void handleRipResponse(RIPv2 ripPacketPv2, Iface inIface, int sourceIp) {
		System.out.println("****Handle RIP Response****");
		// Add the new RIP entries to the table
		for(RIPv2Entry r: ripPacketPv2.getEntries()) {
			r.setMetric(r.getMetric() + 1);// assume metric to adjacent router is 1
			boolean res = ripP.addRIPEntry(r, sourceIp);
			
			if (res) {
				// update the route table
				boolean ex = routeTable.update(r.getAddress(), r.getSubnetMask(), sourceIp, inIface);
				
				if(!ex) {
					// Adds if it does not exist
					routeTable.insert(r.getAddress(), r.getSubnetMask(), sourceIp, inIface);
				}
				
			}
			synchronized (RipProtocol.RIP_ENTRIES_LOCK) {
			//TODO test
			ripP.print();
			}
		}
		
	}
	
	/**
	 * Handles rip packets sent from adjacent routers 
	 * @param ipPacket
	 * @param inIface
	 */
    private void handleRipPacket(IPv4 ipPacket, Iface inIface){
    	// Get the UDP packet containing the rip packet
    	if (!(ipPacket.getPayload() instanceof UDP)) {
    		return;
    	}
		UDP udpPacket = (UDP)ipPacket.getPayload();
		System.out.println("****Handle RIP packet****");
		
		// Verify checksum
		short origCksum = udpPacket.getChecksum();
		udpPacket.resetChecksum();
		byte[] serialized = udpPacket.serialize();
		udpPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = udpPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }
		
		RIPv2 ripPacketPv2 = (RIPv2)udpPacket.getPayload();
		int sourceIp = ipPacket.getSourceAddress();
		if(ripPacketPv2.getCommand() == RIPv2.COMMAND_REQUEST){
			handleRipRequest(ripPacketPv2, inIface, sourceIp);
		} else if(ripPacketPv2.getCommand() == RIPv2.COMMAND_RESPONSE) {
			handleRipResponse(ripPacketPv2, inIface, sourceIp);
		}
    }

	private void handleIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		System.out.println("Handle IP packet");

		// Verify checksum
		short origCksum = ipPacket.getChecksum();
		ipPacket.resetChecksum();
		byte[] serialized = ipPacket.serialize();
		ipPacket.deserialize(serialized, 0, serialized.length);
		short calcCksum = ipPacket.getChecksum();
		if (origCksum != calcCksum)
		{ return; }

		// Check TTL
		ipPacket.setTtl((byte)(ipPacket.getTtl()-1));
		if (0 == ipPacket.getTtl())
		{ return; }
		
		// Reset checksum now that TTL is decremented
		ipPacket.resetChecksum();
		
		// Check if packet is destined for one of router's interfaces
		for (Iface iface : this.interfaces.values())
		{
			if (ipPacket.getDestinationAddress() == iface.getIpAddress() ||
					ipPacket.getDestinationAddress() == RipProtocol.MULTICAST_RIP_IP){ 	
				// this router cares about this packet
				if(ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
					UDP udpPacket = (UDP) ipPacket.getPayload();
					if(udpPacket.getDestinationPort() == UDP.RIP_PORT && udpPacket.getSourcePort() == UDP.RIP_PORT) {
						//This is a RIP packet
						this.handleRipPacket(ipPacket, inIface);
						return;
					}
					
				}
				return;
			}
		}
		
		// Do route lookup and forward
		this.forwardIpPacket(etherPacket, inIface);
	}

	private void forwardIpPacket(Ethernet etherPacket, Iface inIface)
	{
		// Make sure it's an IP packet
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{ return; }
		System.out.println("Forward IP packet");

		// Get IP header
		IPv4 ipPacket = (IPv4)etherPacket.getPayload();
		int dstAddr = ipPacket.getDestinationAddress();

		// Find matching route table entry 
		RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

		// If no entry matched, do nothing
		if (null == bestMatch)
		{ return; }

		// Make sure we don't sent a packet back out the interface it came in
		Iface outIface = bestMatch.getInterface();
		if (outIface == inIface)
		{ return; }

		// Set source MAC address in Ethernet header
		etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

		// If no gateway, then nextHop is IP destination
		int nextHop = bestMatch.getGatewayAddress();
		if (0 == nextHop)
		{ nextHop = dstAddr; }

		// Set destination MAC address in Ethernet header
		ArpEntry arpEntry = this.arpCache.lookup(nextHop);
		if (null == arpEntry)
		{ return; }
		etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

		this.sendPacket(etherPacket, outIface);
	}
}
