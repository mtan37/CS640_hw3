package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;

/**
  * @author Anubhavnidhi Abhashkumar and Aaron Gember-Jacobson
  */
public class RIPv2Entry 
{
    public static final short ADDRESS_FAMILY_IPv4 = 2;

    protected short addressFamily;
    protected short routeTag;//out of the scope of assignment 3
	protected int address;//address of the destination router
	protected int subnetMask;//subnet mask of the destination address(address)
	protected int nextHopAddress;//0 if the address = nexthop
	protected int metric;//indicating the distance to the destination. 16 = infinity
	protected short ttl;//time to live in seconds

    public RIPv2Entry()
    { }

    public RIPv2Entry(int address, int subnetMask, int nextHop, int metric)
    {
    	this.routeTag = 0;// hard coded to 0
        this.addressFamily = ADDRESS_FAMILY_IPv4;
        this.address = address;
        this.subnetMask = subnetMask;
        this.nextHopAddress = nextHop;
        this.metric = metric;
        this.ttl = 30;//30 seconds
    }

    public RIPv2Entry(RIPv2Entry copy){ 
    	this.routeTag = copy.routeTag;
        this.addressFamily = copy.addressFamily;
        this.address = copy.address;
        this.subnetMask = copy.subnetMask;
        this.nextHopAddress = copy.nextHopAddress;
        this.metric = copy.metric;
        this.ttl = copy.ttl;//30 seconds
    }
    
	public String toString()
	{
        return String.format("RIPv2Entry : {addressFamily=%d, routeTag=%d, address=%s, subnetMask=%s, nextHopAddress=%s, metric=%d}", 
                this.addressFamily, this.routeTag, 
                IPv4.fromIPv4Address(this.address), 
                IPv4.fromIPv4Address(this.subnetMask),
                IPv4.fromIPv4Address(this.nextHopAddress), this.metric);
	}

	/**
	 * reset ttl to initial value 30s
	 */
	public void resetTtl() {
		this.ttl = 30;
	}
	
	/**
	 * Decrease ttl by given decrement.
	 * @param decrement
	 * @return
	 */
	public short decreaseTtl(short decrement) {
		
		if (decrement >= ttl) {
			ttl = 0;
		} 
		else if (decrement < 0) {
			return this.ttl;
		}
		else {
			ttl -= decrement;
		}
		
		return this.ttl;
	}
	
	public short getTtl() {
		return this.ttl;
	}
	
    public short getAddressFamily()
    { return this.addressFamily; }

    public void setAddressFamily(short addressFamily)
    { this.addressFamily = addressFamily; }

    public short getRouteTag()
    { return this.routeTag; }

    public void setRouteTag(short routeTag)
    { this.routeTag = routeTag; }

	public int getAddress()
	{ return this.address; }

	public void setAddress(int address)
	{ this.address = address; }

	public int getSubnetMask()
	{ return this.subnetMask; }

	public void setSubnetMask(int subnetMask)
	{ this.subnetMask = subnetMask; }

	public int getNextHopAddress()
	{ return this.nextHopAddress; }

	public void setNextHopAddress(int nextHopAddress)
	{ this.nextHopAddress = nextHopAddress; }

    public int getMetric()
    { return this.metric; }

    public void setMetric(int metric)
    { this.metric = metric; }

	public byte[] serialize() 
    {
		int length = 2*2 + 4*4;
		byte[] data = new byte[length];
		ByteBuffer bb = ByteBuffer.wrap(data);

		bb.putShort(this.addressFamily);
		bb.putShort(this.routeTag);
        bb.putInt(this.address);
        bb.putInt(this.subnetMask);
        bb.putInt(this.nextHopAddress);
        bb.putInt(this.metric);
		return data;
	}

	public RIPv2Entry deserialize(byte[] data, int offset, int length) 
	{
		ByteBuffer bb = ByteBuffer.wrap(data, offset, length);

		this.addressFamily = bb.getShort();
		this.routeTag = bb.getShort();
        this.address = bb.getInt();
        this.subnetMask = bb.getInt();
        this.nextHopAddress = bb.getInt();
        this.metric = bb.getInt();
		return this;
	}

    public boolean equals(Object obj)
    {
        if (this == obj)
        { return true; }
        if (null == obj)
        { return false; }
        if (!(obj instanceof RIPv2Entry))
        { return false; }
        RIPv2Entry other = (RIPv2Entry)obj;
        if (this.addressFamily != other.addressFamily)
        { return false; }
        if (this.routeTag != other.routeTag)
        { return false; }
        if (this.address != other.address)
        { return false; }
        if (this.subnetMask != other.subnetMask)
        { return false; }
        if (this.nextHopAddress != other.nextHopAddress)
        { return false; }
        if (this.metric != other.metric)
        { return false; }
        return true; 
    }
}
