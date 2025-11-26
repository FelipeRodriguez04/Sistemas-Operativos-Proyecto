package vmmanager;

import vmsimulation.BackingStore;
import vmsimulation.BitwiseToolbox;
import vmsimulation.MainMemory;
import vmsimulation.MemoryException;

import java.util.ArrayList;

public class VirtualMemoryManagerV2 {
	MainMemory memory;	
	BackingStore disk;	
	Integer pageSize;	

	private static class PageTableEntry {
		boolean valid;
		int frameNumber;
	}

	PageTableEntry[] pageTable;
	int numFrames;
	int numPages;

	ArrayList<Integer> freeFrames=new ArrayList<>();
	ArrayList<Integer> fifoQueue=new ArrayList<>();

	int pageFaultCount=0;
	int bytesTransferred=0;

    private int log2(int x) {
        return (int)(Math.log(x)/Math.log(2));
    }


	public VirtualMemoryManagerV2(MainMemory memory, BackingStore disk, Integer pageSize) throws MemoryException {
		this.memory=memory;
		this.disk=disk;
		this.pageSize=pageSize;

		this.numFrames=memory.size()/pageSize;
		this.numPages=disk.size()/pageSize;

		pageTable = new PageTableEntry[numPages];
		for (int i=0; i < numPages; i++) {
			pageTable[i]=new PageTableEntry();
			pageTable[i].valid = false;
			pageTable[i].frameNumber = -1;
		}

		for (int f=0; f < numFrames; f++) {
			freeFrames.add(f);
		}
	}

	private int extractVirtualAddress(Integer fourByteBinaryString) {
		int virtualAddressBits=log2(numPages*pageSize);
		return BitwiseToolbox.extractBits(fourByteBinaryString, 0, virtualAddressBits);
	}

	private int offsetBits() {
		return log2(pageSize);
	}

	private int getPageNumber(int virtualAddress) {
		int offBits=offsetBits();
		return virtualAddress>>offBits;
	}

	private int getOffset(int virtualAddress) {
		int offBits=offsetBits();
		int mask=(1<<offBits) - 1;
		return virtualAddress & mask;
	}

	private String formatVirtualAddress(int virtualAddress) {
		int bits=log2(numPages * pageSize);
		String s=Integer.toBinaryString(virtualAddress);
		while (s.length()<bits) {
			s="0"+s;
		}
		return s;
	}

	private String formatPhysicalAddress(int physicalAddress) {
		int bits=log2(memory.size());
		String s=Integer.toBinaryString(physicalAddress);
		while (s.length()<bits) {
			s="0"+s;
		}
		return s;
	}


	private void readPageFromDisk(int pageNumber, int frame) throws MemoryException {
		int ramBase =frame * pageSize;
		byte[] pageData=disk.readPage(pageNumber);

		for (int i=0; i < pageSize; i++) {
			byte val=pageData[i];
			memory.writeByte(ramBase+i, val);
			bytesTransferred++;
		}
	}

	private void writePageToDisk(int pageNumber, int frame) throws MemoryException {
		int ramBase =frame * pageSize;
		byte[] pageData = new byte[pageSize];
		for (int i=0; i < pageSize; i++) {
			byte val=memory.readByte(ramBase+i);
			pageData[i] = val;
			bytesTransferred++;
		}

		disk.writePage(pageNumber, pageData);
	}

	private int handlePageFault(int pageNumber) throws MemoryException {
		pageFaultCount++;

		int frame;
		if (!freeFrames.isEmpty()) {
			frame=freeFrames.remove(0);
		} else {
			int victimPage=fifoQueue.remove(0);
			PageTableEntry victimEntry = pageTable[victimPage];

			System.out.println("Evicting page " + victimPage);
			writePageToDisk(victimPage, victimEntry.frameNumber);
			victimEntry.valid = false;
			frame=victimEntry.frameNumber;
		}

		readPageFromDisk(pageNumber, frame);
		e.valid=true;
		e.frameNumber=frame;
		fifoQueue.add(pageNumber);
		System.out.println("Bringing page " + pageNumber + " into frame " + frame);
		return frame;
	}


	public void writeByte(Integer fourByteBinaryString, Byte value) throws MemoryException {
		int virtualAddress=extractVirtualAddress(fourByteBinaryString);
		int page=getPageNumber(virtualAddress);
		int offset=getOffset(virtualAddress);

		int frame;
		if (!pageTable[page].valid) {
			frame=handlePageFault(page);
		} else {
			System.out.println("Page " + page + " is in memory");
			frame=pageTable[page].frameNumber;
		}

		int physicalAddress=frame * pageSize + offset;
		memory.writeByte(physicalAddress, value);

		System.out.println("RAM: @" + formatPhysicalAddress(physicalAddress) +
				" <-- " + value);
	}

	public Byte readByte(Integer fourByteBinaryString) throws MemoryException {
		int virtualAddress=extractVirtualAddress(fourByteBinaryString);
		int page=getPageNumber(virtualAddress);
		int offset=getOffset(virtualAddress);

		int frame;
		if (!pageTable[page].valid) {
			frame=handlePageFault(page);
		} else {
			System.out.println("Page " + page + " is in memory");
			frame=pageTable[page].frameNumber;
		}

		int physicalAddress=frame * pageSize + offset;
		byte value=memory.readByte(physicalAddress);

		System.out.println("RAM: @" + formatPhysicalAddress(physicalAddress) +
				" --> " + value);

		return value;
	}

	public void printMemoryContent() throws MemoryException {
		int memSize=memory.size();
		int bits=log2(memSize);

		for (int addr=0; addr < memSize; addr++) {
			String s=Integer.toBinaryString(addr);
			while (s.length() < bits) {
				s="0" + s;
			}
			byte val=memory.readByte(addr);
			System.out.println(s + ": " + val);
		}
	}

	public void printDiskContent() throws MemoryException {
		int diskSize=disk.size();
		int numPagesLocal=diskSize / pageSize;

		for (int p=0; p < numPagesLocal; p++) {
			StringBuilder sb=new StringBuilder();
			sb.append("PAGE ").append(p).append(": ");

			byte[] pageData=disk.readPage(p);

			for (int i=0; i < pageSize; i++) {
				byte val=pageData[i];
				sb.append(val);
				if (i < pageSize - 1) {
					sb.append(",");
				}
			}
			System.out.println(sb.toString());
		}
	}

	public void writeBackAllPagesToDisk() throws MemoryException {
		for (int page=0; page < numPages; page++) {
			PageTableEntry e=pageTable[page];
			if (e.valid) {
				writePageToDisk(page, e.frameNumber);
			}
		}
	}

	public int getPageFaultCount() {
		return pageFaultCount;
	}

	public int getTransferedByteCount() {
		return bytesTransferred;
	}
}


