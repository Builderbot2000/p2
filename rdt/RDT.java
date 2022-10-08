
/**
 * @author mohamed
 *
 */
package rdt;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RDT {

	public static final int MSS = 100; // Max segement size in bytes
	public static final int RTO = 500; // Retransmission Timeout in msec
	public static final int ERROR = -1;
	public static final int MAX_BUF_SIZE = 3;  
	public static final int GBN = 1;   // Go back N protocol
	public static final int SR = 2;    // Selective Repeat
	public static final int protocol = GBN;
	
	public static double lossRate = 0.0;
	public static Random random = new Random(); 
	public static Timer timer = new Timer();	
	
	private DatagramSocket socket; 
	private InetAddress dst_ip;
	private int dst_port;
	private int local_port; 
	
	private RDTBuffer sndBuf;
	private RDTBuffer rcvBuf;
	
	private ReceiverThread rcvThread;  
	
	
	RDT (String dst_hostname_, int dst_port_, int local_port_) 
	{
		local_port = local_port_;
		dst_port = dst_port_; 
		try {
			 socket = new DatagramSocket(local_port);
			 dst_ip = InetAddress.getByName(dst_hostname_);
		 } catch (IOException e) {
			 System.out.println("RDT constructor: " + e);
		 }
		sndBuf = new RDTBuffer(MAX_BUF_SIZE);
		if (protocol == GBN)
			rcvBuf = new RDTBuffer(1);
		else 
			rcvBuf = new RDTBuffer(MAX_BUF_SIZE);
		rcvThread = new ReceiverThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
		rcvThread.start();
	}
	
	RDT (String dst_hostname_, int dst_port_, int local_port_, int sndBufSize, int rcvBufSize)
	{
		local_port = local_port_;
		dst_port = dst_port_;
		 try {
			 socket = new DatagramSocket(local_port);
			 dst_ip = InetAddress.getByName(dst_hostname_);
		 } catch (IOException e) {
			 System.out.println("RDT constructor: " + e);
		 }
		sndBuf = new RDTBuffer(sndBufSize);
		if (protocol == GBN)
			rcvBuf = new RDTBuffer(1);
		else 
			rcvBuf = new RDTBuffer(rcvBufSize);
		
		rcvThread = new ReceiverThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
		rcvThread.start();
	}
	
	public static void setLossRate(double rate) {lossRate = rate;}
	
	// called by app
	// returns total number of sent bytes  
	public int send(byte[] data, int size) {
		//****** complete
		System.out.println("Send requested. data: ");
		for (byte b : data) System.out.print(b);
		RDTSegment segment = null;
		int loaded = 0;
		int sent = 0;
		for (int i=0; i<size; i++) {
			if (segment == null) segment = new RDTSegment();
			// divide data into segments
			segment.data[loaded] = data[i];
			System.out.println(loaded + "|loaded: " + segment.data[loaded]);
			segment.length++;
			loaded++;
			if (segment.length == MSS-1 || i == size-1) {
				// put each segment into sndBuf
				segment.seqNum = sndBuf.next;
				System.out.println("Trying to place in sndbuf...");
				sndBuf.putNext(segment);
				System.out.println("Placed in sndbuf.");
				// send using udp_send()
				segment.checksum = segment.computeChecksum();
				// segment.dump();
				Utility.udp_send(segment, socket, dst_ip, dst_port);
				System.out.println("Sent.");
				sent += segment.length;
				// schedule timeout for segment(s) 
				System.out.println("Scheduling timout...");
				segment.timeoutHandler = new TimeoutHandler(sndBuf, segment, socket, dst_ip, dst_port);
				Timer timeoutTimer = new Timer();
				timeoutTimer.schedule(segment.timeoutHandler, RTO);
				// reset segment husk
				segment = null;
				loaded = 0;
			}
		}
		System.out.println("Send process end.");
		// sndBuf.dump();
		return sent;
	}

	// retransmit an existing segment without reconstructing the segment
	public static void scheduleTimeout(RDTSegment segment) {
		Timer timeoutTimer = new Timer();
		timeoutTimer.schedule(segment.timeoutHandler, RTO);
	}
	
	// called by app
	// receive one segment at a time
	// returns number of bytes copied in buf
	public int receive (byte[] buf, int size)
	{
		//*****  complete
		RDTSegment segment = rcvBuf.getNext();; 
		if (segment == null) return 0; 
		int count = 0;
		while (count<segment.length && count<size) {
			buf[count] = segment.data[count];
			count++;
		}
		return count;   // fix
	}
	
	// called by app
	public void close() {
		// OPTIONAL: close the connection gracefully
		// you can use TCP-style connection termination process
	}
	
}  // end RDT class 


class RDTBuffer {
	public RDTSegment[] buf;
	public int size;	
	public int base;
	public int next;
	public Semaphore semMutex; // for mutual execlusion
	public Semaphore semFull; // #of full slots
	public Semaphore semEmpty; // #of Empty slots
	
	RDTBuffer (int bufSize) {
		buf = new RDTSegment[bufSize];
		for (int i=0; i<bufSize; i++)
			buf[i] = null;
		size = bufSize;
		base = next = 0;
		semMutex = new Semaphore(1, true);
		semFull =  new Semaphore(0, true);
		semEmpty = new Semaphore(bufSize, true);
	}

	
	
	// Put a segment in the next available slot in the buffer
	public void putNext(RDTSegment seg) {		
		try {
			System.out.println("Trying to acquire empty slot.");
			// this.dump();
			semEmpty.acquire(); // wait for an empty slot 
			System.out.println("Empty slot acquired.");
			semMutex.acquire(); // wait for mutex 
				buf[next%size] = seg;
				next++;  
			semMutex.release();
			semFull.release(); // increase #of full slots
		} catch(InterruptedException e) {
			System.out.println("Buffer put(): " + e);
		}
	}
	
	// return the next in-order segment
	public RDTSegment getNext() {
		// **** Complete
		RDTSegment seg = null;
		try {
			semMutex.acquire(); // wait for mutex 
				seg = buf[next%size]; 
			semMutex.release();
			this.semEmpty.release();
		} catch(InterruptedException e) {
			System.out.println("Buffer get(): " + e);
		}
		return seg;  // fix 
	}
	
	// Put a segment in the *right* slot based on seg.seqNum
	// used by receiver in Selective Repeat
	public void putSeqNum (RDTSegment seg) {
		// ***** compelte

	}
	
	// for debugging
	public void dump() {
		System.out.println("---- buffer[" + buf.length + "] ----");
		// Complete, if you want to 
		for (int i=0; i<buf.length; i++) {
			if (buf[i] != null) {
				System.out.println("|" + buf[i].seqNum + "|");
				// buf[i].printHeader();
				// buf[i].printData();
			}
			else {
				System.out.println("|NULL|");
			}
		}
		System.out.println("--- end buffer ---");
	}
} // end RDTBuffer class



class ReceiverThread extends Thread {
	RDTBuffer rcvBuf, sndBuf;
	DatagramSocket socket;
	InetAddress dst_ip;
	int dst_port;
	
	ReceiverThread (RDTBuffer rcv_buf, RDTBuffer snd_buf, DatagramSocket s, 
			InetAddress dst_ip_, int dst_port_) {
		rcvBuf = rcv_buf;
		sndBuf = snd_buf;
		socket = s;
		dst_ip = dst_ip_;
		dst_port = dst_port_;
	}	
	public void run() {
		
		// *** complete 
		// Essentially:  while(cond==true){  // may loop for ever if you will not implement RDT::close()  
		//                socket.receive(pkt)
		//                seg = make a segment from the pkt
		//                verify checksum of seg
		//	              if seg contains ACK, process it potentailly removing segments from sndBuf
		//                if seg contains data, put the data in rcvBuf and do any necessary 
		//                             stuff (e.g, send ACK)
		//
		while (true) {
			try {
				byte[] pktBuf = new byte[RDT.MSS];
				DatagramPacket rcvPacket = new DatagramPacket(pktBuf, RDT.MSS);
				System.out.println("started listening...");
				socket.receive(rcvPacket);
				System.out.println("received something!");
				RDTSegment segment = new RDTSegment();
				makeSegment(segment, rcvPacket.getData());
				segment.dump();
				if (segment.containsAck()) {
					System.out.println("ACK received! seq: " + segment.ackNum);
					switch (RDT.protocol) {
						case RDT.GBN:
							for (int i=0; i<sndBuf.size; i++) {
								if (segment.ackNum >= sndBuf.buf[i].seqNum) {
									sndBuf.buf[i].ackReceived = true;
									sndBuf.buf[i] = null;
									sndBuf.semEmpty.release(); // increase #of empty slots
									System.out.println("cleared slot: " + i);
								}
							}
						case RDT.SR:
							for (int i=0; i<sndBuf.size; i++) {
								if (segment.ackNum == sndBuf.buf[i].seqNum) {
									sndBuf.buf[i].ackReceived = true;
									sndBuf.buf[i] = null;
									sndBuf.semEmpty.release(); // increase #of empty slots
									System.out.println("cleared slot: " + i);
								}
							}
					}
					sndBuf.dump();
				}
				if (segment.containsData() && segment.isValid()) {
					switch (RDT.protocol) {
						case RDT.GBN:
							rcvBuf.putNext(segment);
							RDTSegment ackSR = new RDTSegment();
							ackSR.ackNum = segment.seqNum;
							ackSR.flags = 1;
							if (segment.seqNum == rcvBuf.base + 1) rcvBuf.base++;
							ackSR.seqNum = rcvBuf.base;
							Utility.udp_send(ackSR, socket, dst_ip, dst_port);
							// ackSR.dump();
							System.out.println("ACK sent!");
							// rcvBuf.dump();
							break;
						case RDT.SR:
							rcvBuf.putNext(segment);
							RDTSegment ackGBN = new RDTSegment();
							ackGBN.ackNum = segment.seqNum;
							ackGBN.flags = 1;
							ackGBN.seqNum = segment.seqNum;
							Utility.udp_send(ackGBN, socket, dst_ip, dst_port);
							// ackGBN.dump();
							System.out.println("ACK sent!");
							// rcvBuf.dump();
							break;
						default:
							System.out.println("Error in ReceiverThread:run(): unknown protocol");
					}
				}
			} 
			catch (IOException e) {
				System.out.println("receive packet failed!");
				e.printStackTrace();
			}
		}
	}
	
	
//	 create a segment from received bytes 
	void makeSegment(RDTSegment seg, byte[] payload) {
	
		seg.seqNum = Utility.byteToInt(payload, RDTSegment.SEQ_NUM_OFFSET);
		seg.ackNum = Utility.byteToInt(payload, RDTSegment.ACK_NUM_OFFSET);
		seg.flags  = Utility.byteToInt(payload, RDTSegment.FLAGS_OFFSET);
		seg.checksum = Utility.byteToInt(payload, RDTSegment.CHECKSUM_OFFSET);
		seg.rcvWin = Utility.byteToInt(payload, RDTSegment.RCV_WIN_OFFSET);
		seg.length = Utility.byteToInt(payload, RDTSegment.LENGTH_OFFSET);
		//Note: Unlike C/C++, Java does not support explicit use of pointers! 
		// we have to make another copy of the data
		// This is not effecient in protocol implementation
		for (int i=0; i< seg.length; i++)
			seg.data[i] = payload[i + RDTSegment.HDR_SIZE]; 
	}
	
} // end ReceiverThread class

