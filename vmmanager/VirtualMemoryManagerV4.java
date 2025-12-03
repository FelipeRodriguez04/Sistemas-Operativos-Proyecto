package vmmanager;

import vmsimulation.BackingStore;
import vmsimulation.BitwiseToolbox;
import vmsimulation.MainMemory;
import vmsimulation.MemoryException;

public class VirtualMemoryManagerV4 {

    MainMemory memory;
    BackingStore disk;
    Integer pageSize;

    private int[] pageTable;      
    private int numPages;
    private int numFrames;

    private int offsetBits;
    private int physAddrBits;
    private int virtAddrBits;

    private boolean[] dirty;

    private long[] lastUsed;
    private long timeCounter=0;

    private int nextFreeFrame=0;     
    private int pageFaultCount=0;
    private int transferredByteCount=0;

    public VirtualMemoryManagerV4(MainMemory memory,
                                  BackingStore disk,
                                  Integer pageSize) throws MemoryException {

        this.memory=memory;
        this.disk=disk;
        this.pageSize=pageSize;

        int memSize=memory.size();
        int diskSize=disk.size();

        this.offsetBits=log2(pageSize);
        this.physAddrBits=log2(memSize);
        this.virtAddrBits=log2(diskSize);

        this.numFrames=memSize / pageSize;
        this.numPages=diskSize / pageSize;

        pageTable=new int[numPages];
        dirty=new boolean[numPages];
        lastUsed=new long[numPages];
        for (int i = 0; i < numPages; i++) {
            pageTable[i] = -1;   
            dirty[i] = false;   
            lastUsed[i] = 0;     
        }
    }

    private int log2(int x) {
        return (int) (Math.log(x) / Math.log(2));
    }

    private int getPageNumber(int virtualAddress) {
        return BitwiseToolbox.extractBits(virtualAddress, offsetBits, virtAddrBits - 1);
    }

    private int getOffset(int virtualAddress) {
        return BitwiseToolbox.extractBits(virtualAddress, 0, offsetBits - 1);
    }

    private int ensurePageInMemory(int pageNumber) throws MemoryException {
        int frame=pageTable[pageNumber];
        if (frame != -1) {
            System.out.println("Page " + pageNumber + " is in memory");
            lastUsed[pageNumber]=++timeCounter;
            return frame;
        }
        pageFaultCount++;

        if (nextFreeFrame < numFrames) {
            frame=nextFreeFrame;
            nextFreeFrame++;
            System.out.println("Bringing page " + pageNumber + " into frame " + frame);
            loadPageIntoFrame(pageNumber, frame);
            dirty[pageNumber]=false;                 
            lastUsed[pageNumber]=++timeCounter;      
            pageTable[pageNumber]=frame;
            return frame;
        }

        int victimPage=-1;
        long oldestTime=Long.MAX_VALUE;
        for (int p=0; p < numPages; p++) {
            if (pageTable[p]!=-1) { 
                if (lastUsed[p]<oldestTime) {
                    oldestTime=lastUsed[p];
                    victimPage=p;
                }
            }
        }

        int victimFrame = pageTable[victimPage];
        if (!dirty[victimPage]) {
            System.out.println("Evicting page " + victimPage + " (NOT DIRTY)");
        } 
        else {
            System.out.println("Evicting page " + victimPage);
            writePageToDisk(victimPage, victimFrame);
            dirty[victimPage]=false;   
        }
        pageTable[victimPage]=-1;
        lastUsed[victimPage]=0;         
        System.out.println("Bringing page " + pageNumber + " into frame " + victimFrame);
        loadPageIntoFrame(pageNumber, victimFrame);
        dirty[pageNumber]=false;
        lastUsed[pageNumber]=++timeCounter;
        pageTable[pageNumber]=victimFrame;
        return victimFrame;
    }

    private void loadPageIntoFrame(int pageNumber, int frame) throws MemoryException {
        byte[] pageData=disk.readPage(pageNumber);
        int baseAddr=frame * pageSize;
        for (int i=0; i < pageSize; i++) {
            memory.writeByte(baseAddr + i, pageData[i]);
        }
        transferredByteCount+=pageSize; 
    }

    private void writePageToDisk(int pageNumber, int frame) throws MemoryException {
        byte[] data=new byte[pageSize];
        int baseAddr=frame * pageSize;
        for (int i=0; i < pageSize; i++) {
            data[i]=memory.readByte(baseAddr + i);
        }
        disk.writePage(pageNumber, data);
        transferredByteCount+=pageSize; 
    }

    public void writeByte(Integer fourByteBinaryString, Byte value) throws MemoryException {
        int va=fourByteBinaryString;
        int pageNumber=getPageNumber(va);
        int offset=getOffset(va);
        int frame=ensurePageInMemory(pageNumber);
        int physicalAddress=frame * pageSize + offset;
        memory.writeByte(physicalAddress, value.byteValue());
        dirty[pageNumber]=true;
        lastUsed[pageNumber]=++timeCounter;
        String bitString=BitwiseToolbox.getBitString(physicalAddress, physAddrBits - 1);
        System.out.println("RAM: @" + bitString + " <-- " + value);
    }

    public Byte readByte(Integer fourByteBinaryString) throws MemoryException {
        int va=fourByteBinaryString;
        int pageNumber=getPageNumber(va);
        int offset=getOffset(va);
        int frame=ensurePageInMemory(pageNumber);
        int physicalAddress=frame * pageSize + offset;
        byte value=memory.readByte(physicalAddress);
        lastUsed[pageNumber]=++timeCounter;
        String bitString=BitwiseToolbox.getBitString(physicalAddress, physAddrBits - 1);
        System.out.println("RAM: @" + bitString + " --> " + value);
        return value;
    }

    public void printMemoryContent() throws MemoryException {
        int memSize=memory.size();
        for (int addr=0; addr < memSize; addr++) {
            String addrBits=BitwiseToolbox.getBitString(addr, physAddrBits - 1);
            byte value=memory.readByte(addr);
            System.out.println(addrBits + ": " + value);
        }
    }

    public void printDiskContent() throws MemoryException {
        int diskSize=disk.size();
        int pages=diskSize / pageSize;
        for (int p=0; p < pages; p++) {
            byte[] pageData=disk.readPage(p);
            System.out.print("PAGE " + p + ": ");
            for (int i=0; i < pageSize; i++) {
                System.out.print(pageData[i]);
                if (i < pageSize - 1) System.out.print(",");
            }
            System.out.println();
        }
    }

    public void writeBackAllPagesToDisk() throws MemoryException {
        for (int page=0; page < numPages; page++) {
            int frame=pageTable[page];
            if (frame!=-1 && dirty[page]) {
                byte[] data=new byte[pageSize];
                int baseAddr=frame * pageSize;
                for (int i=0; i < pageSize; i++) {
                    data[i]=memory.readByte(baseAddr + i);
                }
                disk.writePage(page, data);
                transferredByteCount+=pageSize;
                dirty[page]=false;
            }
        }
    }

    public int getPageFaultCount() {
        return pageFaultCount;
    }

    public int getTransferedByteCount() {
        return transferredByteCount;
    }
}
