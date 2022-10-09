/* Kevin Tang (kta76) */
/**
 * @author mohamed
 *
 */

package rdt;

import java.io.*;
import java.net.*;
import java.util.*;

public class TestClient {

	/**
	 * 
	 */
	public TestClient() {
		
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		 if (args.length != 3) {
	         System.out.println("Required arguments: dst_hostname dst_port local_port");
	         return;
	      }
		 String hostname = args[0];
	     int dst_port = Integer.parseInt(args[1]);
	     int local_port = Integer.parseInt(args[2]);
	     	      
	     RDT rdt = new RDT(hostname, dst_port, local_port, 3, 5);
	     RDT.setLossRate(0.2);
	  
		 int msgSize = 45;
	     byte[] buf = new byte[RDT.MSS];
	     byte[] data = new byte[msgSize];
	     for (int i=0; i<msgSize; i++)
	    	 data[i] = 0;
	     rdt.send(data, msgSize);
		 
	     for (int i=0; i<msgSize; i++)
	    	 data[i] = 1;
	     rdt.send(data, msgSize);
	     
	     for (int i=0; i<msgSize; i++)
	    	 data[i] = 2;
	     rdt.send(data, msgSize);
	     
	     for (int i=0; i<msgSize; i++)
	    	 data[i] = 3;
	     rdt.send(data, msgSize);

	     for (int i=0; i<msgSize; i++)
	    	 data[i] = 4;
	     rdt.send(data, msgSize);
	 
	     
	     System.out.println(System.currentTimeMillis() + ":Client has sent all data " );
	     System.out.flush();
	     
	     rdt.receive(buf, RDT.MSS);
	     rdt.close();
	     System.out.println("Client is done " );
	}

}
