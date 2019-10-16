/*
 * Copyright (C) 2019 Jaroslaw Karwik <jaroslaw.karwik@gmail.com>
 * 
 * Based on original code from Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.machine.reference.driver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.Action;
import javax.swing.Icon;

import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.ReferenceDriver;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceHeadMountable;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.driver.wizards.SmallSmtExtServerWizard;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Head;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;

public class SmallSmtExtServerDriver implements ReferenceDriver , Runnable {

	
	class DriverResponse {
		public boolean valid;
		public boolean wait;
    	public int     status;    	
    	public double  value;
    	public double  x;
    	public double  y;
    	public double  z1;
    	public double  c1;
    	public double  z2;
    	public double  c2;
    	public double  z3;
    	public double  c3;
    	public double  z4;
    	public double  c4;
    	
    	public DriverResponse(){
    		 valid = false;
    		 wait = false;
    	}
	}
		   
	
    private HashMap<Head, Location> headLocations = new HashMap<>();

    private Object commandLock = new Object();
    private Thread readerThread;
    
    private DatagramSocket socketOpenPnp;
    private DatagramSocket socketExt;
    private InetAddress address;
    
    private int packetId;
    private Queue<String> responseQueue = new ConcurrentLinkedQueue<>();
   
   
    

    
    private boolean enabled;
    private boolean connected;
    private boolean disconnectRequested;
    private DriverResponse lastResponse;
 
    
    public static final int UDP_PORT_OPENPNP = 9072;
    public static final int UDP_PORT_DRIVER  = 9070;
    public static final int UDP_TIMEOUT = 500;
    public static final int UDP_PROTOCOL_VERSION = 1;
    

    
    
    @Attribute(required = false)
    private double feedRateMmPerMinute;
    
    public SmallSmtExtServerDriver() throws Exception {
        connect();
    }

    /**
     * Gets the Location object being tracked for a specific Head. This is the absolute coordinates
     * of a virtual Head on the machine.
     * 
     * @param head
     * @return
     */
    protected Location getHeadLocation(Head head) {
        Location l = headLocations.get(head);
        if (l == null) {
            l = new Location(LengthUnit.Millimeters, 0, 0, 0, 0);
            setHeadLocation(head, l);
        }
        return l;
    }

    protected void setHeadLocation(Head head, Location l) {
        headLocations.put(head, l);
    }

    protected void updateMachineLocation(ReferenceHeadMountable hm)
    {
    	// Provide live updates to the Machine as the move progresses.
    	((ReferenceMachine) Configuration.get().getMachine()).fireMachineHeadActivity(hm.getHead());
    }
    
    
    @Override
    public void home(ReferenceHead head) throws Exception {
        Logger.debug("home()");
        checkEnabled();
        send("home()");
        setHeadLocation(head, getHeadLocation(head).derive(0.0, 0.0, 0.0, 0.0));
    }

    /**
     * Return the Location of a specific ReferenceHeadMountable on the machine. We get the
     * coordinates for the Head the object is attached to, and then we add the offsets assigned to
     * the object to make the coordinates correct for that object.
     */
    @Override
    public Location getLocation(ReferenceHeadMountable hm) {
        return getHeadLocation(hm.getHead()).add(hm.getHeadOffsets());
    }

    /**
     * Commands the driver to move the given ReferenceHeadMountable to the specified Location at the
     * given speed. Please see the comments for this method in the code for some important
     * considerations when writing your own driver.
     */
    @Override
    public void moveTo(ReferenceHeadMountable hm, Location location, double speed) throws Exception {
    	
        Logger.debug("moveTo({}, {}, {})", hm, location, speed);
        checkEnabled();

        // Subtract the offsets from the incoming Location. This converts the
        // offset coordinates to driver / absolute coordinates.
        location = location.subtract(hm.getHeadOffsets());

        // Convert the Location to millimeters, since that's the unit that	
        // this driver works in natively.
        location = location.convertToUnits(LengthUnit.Millimeters);

        // Get the current location of the Head that we'll move
        Location hl = getHeadLocation(hm.getHead());

        send(String.format(Locale.US, "moveTo(%s,%f,%f,%f,%f,%f)", hm.getName(), location.getX(), location.getY(),
                location.getZ(), location.getRotation(),speed));

        
        // Now that movement is complete, update the stored Location to the new
        // Location, unless the incoming Location specified an axis with a value
        // of NaN. NaN is interpreted to mean "Don't move this axis" so we don't
        // update the value, either.

        hl = hl.derive(Double.isNaN(location.getX()) ? null : location.getX(),
                Double.isNaN(location.getY()) ? null : location.getY(),
                Double.isNaN(location.getZ()) ? null : location.getZ(),
                Double.isNaN(location.getRotation()) ? null : location.getRotation());

        setHeadLocation(hm.getHead(), hl);
        
        updateMachineLocation(hm);
    }

    @Override
    public void pick(ReferenceNozzle nozzle) throws Exception {
        Logger.debug("pick({})", nozzle);
        checkEnabled();
        send(String.format(Locale.US, "pick(%s)",nozzle.toString()));

    }

    @Override
    public void place(ReferenceNozzle nozzle) throws Exception {
        Logger.debug("place({})", nozzle);
        checkEnabled();
        send(String.format(Locale.US, "place(%s)",nozzle.toString()));
    }

    @Override
    public void actuate(ReferenceActuator actuator, double value) throws Exception {
        Logger.debug("actuateD({}, {})", actuator, value);
        checkEnabled();
        send(String.format(Locale.US, "actuate(%s,%f)",actuator.toString(),value));
    }

    @Override
    public void actuate(ReferenceActuator actuator, boolean on) throws Exception {
        Logger.debug("actuateB({}, {})", actuator, on);
        checkEnabled();
        send(String.format(Locale.US, "actuate(%s,%d)",actuator.toString(),on == true ? 1:0));
    }
    
    @Override
    public String actuatorRead(ReferenceActuator actuator) throws Exception {
    	
        Logger.debug("actuateR({})", actuator);
        checkEnabled();	
        send(String.format(Locale.US, "actuateRead(%s)",actuator.toString()));
    	        
		return Double.toString(lastResponse.value);
    	
    }
    
    
    @Override
    public void setEnabled(boolean enabled) throws Exception {
        Logger.debug("setEnabled({})", enabled);
        
        if (enabled && !connected) {
            connect();
        }
        
        try
        {
	        if (connected) {
	            send("setEnabled" + (enabled ? "(1)" : "(0)"));
	        }
        }
        catch(Exception e)
        {
           // May happen if server is down
           // We care only for enable case
           if(enabled)
           {
        	   throw new Exception("Driver cannot enable the machine!");
           }
        
        }
        
        if (connected && !enabled) {
            disconnect();
        }     
        
        this.enabled = enabled;
    }

    private void checkEnabled() throws Exception {
        if (!enabled) {
            throw new Exception("Driver is not yet enabled!");
        }
    }



    public synchronized void connect()  throws Exception {    	
    	disconnectRequested = false;
    	
    	try {
    		socketExt = new DatagramSocket();
    		socketOpenPnp  = new DatagramSocket(UDP_PORT_OPENPNP);    		
    		address = InetAddress.getByName("localhost");
    	}
        catch (Exception e) {
        	 throw new Exception("Cannot establish UDP connection to server");
        }	
    	
    	synchronized (commandLock) {
              // Start the reader thread with the commandLock held. This will
              // keep the thread from quickly parsing any responses messages
              // and notifying before we get a change to wait.
              readerThread = new Thread(this);
              readerThread.setDaemon(true);
              readerThread.start();
        }
         
        // Turn off the machine
        try {
        	setEnabled(false);
        }
        catch (Exception e) {
            throw new Exception("Unable to connect!");
        }	
        
        connected = true;   
    }
    
    public synchronized void disconnect() {
        disconnectRequested = true;
        connected = false;

        try {
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.join(3000);
            }
            socketOpenPnp.close();
            socketExt.close();
        }
        catch (Exception e) {
            Logger.error("disconnect()", e);
        }

        disconnectRequested = false;
    }    

  
    @Override
    public Wizard getConfigurationWizard() {
        return new SmallSmtExtServerWizard(this);
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return getClass().getSimpleName();
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }

    
    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard())};
    }
    

        
    

    @Override
    public Action[] getPropertySheetHolderActions() {
        return null;
    }

    @Override
    public Icon getPropertySheetHolderIcon() {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
  
    private DriverResponse processResponse(String response,int requestId) throws Exception
    {
    	String  version   = String.format("V%d",UDP_PROTOCOL_VERSION,packetId);
    	DriverResponse resp = new DriverResponse();
    	
    	String[] array = response.split(":");
    	
    	
    	try
    	{
    		// Response format
	    	// [|Vv|idx|Status|Value|X|Y|Z1|C1|Z2|C2|Z3|C3|Z4|C4|]

    		if(array.length == 16){
		    	if( array[0].contentEquals("[") && array[15].contentEquals("]")){
		    		if( version.contentEquals(array[1]) && (requestId == Integer.parseInt(array[2]))){
		    			resp.status = Integer.parseInt(array[3]);
		    			
		    			if (array[4].contentEquals("nan"))
		    			{
		    				resp.value  = Double.NaN;
		    			}
		    			else
		    			{		    			
		    				resp.value  = Double.parseDouble(array[4]);
		    			}
		    			
		    			resp.x = Double.parseDouble(array[5]);
		    			resp.y = Double.parseDouble(array[6]);
		    			resp.z1 = Double.parseDouble(array[7]);
		    			resp.c1 = Double.parseDouble(array[8]);
		    			resp.z2 = Double.parseDouble(array[9]);
		    			resp.c2 = Double.parseDouble(array[10]);
		    			resp.z3 = Double.parseDouble(array[11]);
		    			resp.c3 = Double.parseDouble(array[12]);
		    			resp.z4 = Double.parseDouble(array[13]);
		    			resp.c4 = Double.parseDouble(array[14]);		    			
		    			resp.valid = true;
		    			resp.wait = false;
		    		}
		    	}
    		}
    		else if (array.length == 5){
    	    	if( array[0].contentEquals("[") && array[4].contentEquals("]")){
		    		if( version.contentEquals(array[1]) && (requestId == Integer.parseInt(array[2]))){
		    			resp.status = Integer.parseInt(array[3]);
		    			
		    			resp.valid = true;
		    			resp.wait = true;
		    			
		    		}
		    	}
    		}
	    	
    	}
    	catch(Exception e)
    	{
    		resp.valid = false;
    	}

    	if(resp.valid == false)    
    	{
    		Logger.debug("Message invalid");
    	}
    	else
    	{
    		Logger.debug("Message processed(ok)");
    		if (resp.status < 0)
    		{
    			// Fatal error, the machine cannot continue
    			throw new Exception("Fatal error in driver, cannot continue. See machine log!!	");
    		}
    		
    	}
    		    	
    	return resp;
    }
    
    private void send(String s) throws Exception {
        try {
        	boolean sendDone = false;
        	String  response;

        	
        	 synchronized (commandLock) {
        		 packetId = packetId + 1;
        		 s.replace(" ", "");
        		 
        		 String message = String.format("<:V%d:%d:%s:>",UDP_PROTOCOL_VERSION,packetId,s);	
        		 
        		 Logger.debug("sending({})",message);
        		 
        		 DatagramPacket packet = new DatagramPacket(message.getBytes(),message.length(),address,UDP_PORT_DRIVER);
        		 socketExt.send(packet);
        		 
        		 while(sendDone == false)
        		 {
        			 commandLock.wait(UDP_TIMEOUT);
        			 
        			 if(responseQueue.isEmpty())
        			 {
        				 throw new Exception("No message response!");
        			 }
        			 
        			 while ((response = responseQueue.poll()) != null) {
        				 DriverResponse  result = processResponse(response, packetId);
        				 
        				 if(result.valid == true)
        				 {
        					 lastResponse = result;
        					 
        					 if(result.wait == false)
        					 {
        						 // Still waiting for the command to finish 
        						 sendDone = true;
        					 }
        				 }
            		 }
        		 }
        	 }        	 
        }
        catch (Exception e) {
        	throw new Exception("Driver could not send message to the machine!");
        }
             
    }
    
    
    public void run() {
    	
        byte[] buf = new byte[1024];
    	 
        while (!disconnectRequested) {        	
        	try {
        		
            	socketOpenPnp.setSoTimeout(UDP_TIMEOUT);
	        	DatagramPacket packet = new DatagramPacket(buf, buf.length);
	        	socketOpenPnp.receive(packet);
	        	String received = new String(packet.getData(), 0, packet.getLength());	        	    	        	    
	        	Logger.debug("received({})",received);
	        	    
        	 	// Command response
        		responseQueue.offer(received);	        		
                synchronized (commandLock) {
                    commandLock.notify();
                }	        		       
        	}
        	catch (Exception e) {
        		// Basically it means that server is down
        	}
        }
    }    
    
    
    
}
