/**
 * @author mhefeeda
 *
 */

package rdt;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.TimerTask;

class TimeoutHandler extends TimerTask {
	RDTBuffer sndBuf;
	RDTSegment seg; 
	DatagramSocket socket;
	InetAddress ip;
	int port;
	
	TimeoutHandler (RDTBuffer sndBuf_, RDTSegment s, DatagramSocket sock, 
			InetAddress ip_addr, int p) {
		sndBuf = sndBuf_;
		seg = s;
		socket = sock;
		ip = ip_addr;
		port = p;
	}
	
	public void run() {
		
		System.out.println(System.currentTimeMillis()+ ":Timeout for seg: " + seg.seqNum);
		System.out.flush();
		
		// complete 
		switch(RDT.protocol){
			case RDT.GBN:
				if (seg.ackReceived != true) {
					System.out.println("retransmitting all later packets...");
					for (int i=0; i<sndBuf.size; i++) {
						RDTSegment inflight = sndBuf.buf[i];
						if (seg.seqNum <= inflight.seqNum) {
							Utility.udp_send(inflight, socket, ip, port);
							Boolean rst = seg.timeoutHandler.cancel();
							System.out.println("timeout cancelled for " + inflight.seqNum + ": " + rst);
							seg.timeoutHandler = new TimeoutHandler(sndBuf, inflight, socket, ip, port);
							RDT.scheduleTimeout(inflight);
							System.out.println("resent packet " + inflight.seqNum);
						}
					}
				}
				break;
			case RDT.SR:
				if (seg.ackReceived != true) {
					System.out.println("retransmitting packet...");
					Utility.udp_send(seg, socket, ip, port);
					seg.timeoutHandler = new TimeoutHandler(sndBuf, seg, socket, ip, port);
					RDT.scheduleTimeout(seg);
				}
				break;
			default:
				System.out.println("Error in TimeoutHandler:run(): unknown protocol");
		}
		
	}
} // end TimeoutHandler class

