package vmmanager;

import vmsimulation.BackingStore;
import vmsimulation.BitwiseToolbox;
import vmsimulation.MainMemory;
import vmsimulation.MemoryException;

public class VirtualMemoryManagerV1 {

    MainMemory memory;   
    BackingStore disk;  
    Integer pageSize;    

    private int[] pageTable;
    private int numPages;
    private int numFrames;

    private int offsetBits;    
    private int physAddrBits;  
    private int virtAddrBits;  

    private int nextFreeFrame=0;

    private int pageFaultCount=0;
    private int transferredByteCount=0;

    private int log2(int x) {
        return (int) (Math.log(x) / Math.log(2));
    }

    public VirtualMemoryManagerV1(MainMemory memory,
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

        this.numFrames=memSize/pageSize;
        this.numPages=diskSize/pageSize;

        this.pageTable=new int[numPages];
        for (int i=0; i<numPages; i++) {
            pageTable[i]=-1;
        }
    }

    private int ensurePageInMemory(int pageNumber) throws MemoryException {
        int frame=pageTable[pageNumber];

        if (frame==-1) {
            frame=nextFreeFrame;
            System.out.println("Bringing page "+pageNumber+" into frame "+frame);
            byte[] pageData=disk.readPage(pageNumber);
            int baseAddr = frame * pageSize;
            for (int i=0; i<pageSize; i++) {
                memory.writeByte(baseAddr + i, pageData[i]);
            }
            pageTable[pageNumber]=frame;
            nextFreeFrame++;
            pageFaultCount++;
            transferredByteCount+=pageSize; 
        } else {
            System.out.println("Page "+pageNumber+" is in memory");
        }

        return frame;
    }

    private int getPageNumber(int virtualAddress) {
        return BitwiseToolbox.extractBits(virtualAddress, offsetBits, virtAddrBits - 1);
    }

    private int getOffset(int virtualAddress) {
        return BitwiseToolbox.extractBits(virtualAddress, 0, offsetBits - 1);
    }

    public void writeByte(Integer fourByteBinaryString, Byte value) throws MemoryException {
        int va=fourByteBinaryString;
        int pageNumber=getPageNumber(va);
        int offset=getOffset(va);
        int frame=ensurePageInMemory(pageNumber);
        int physicalAddress=frame*pageSize+offset;
        memory.writeByte(physicalAddress, value.byteValue());
        String bitString = BitwiseToolbox.getBitString(physicalAddress, physAddrBits - 1);
        System.out.println("RAM: @"+bitString+" <-- " + value);
    }

    public Byte readByte(Integer fourByteBinaryString) throws MemoryException {
        int va=fourByteBinaryString;
        int pageNumber=getPageNumber(va);
        int offset=getOffset(va);
        int frame=ensurePageInMemory(pageNumber);
        int physicalAddress=frame*pageSize+offset;
        byte value=memory.readByte(physicalAddress);
        String bitString = BitwiseToolbox.getBitString(physicalAddress, physAddrBits - 1);
        System.out.println("RAM: @"+bitString+" --> " + value);
        return value;
    }

    public void printMemoryContent() throws MemoryException {
        int memSize=memory.size();
        for (int addr=0; addr<memSize; addr++) {
            String addrBits=BitwiseToolbox.getBitString(addr, physAddrBits - 1);
            byte value=memory.readByte(addr);
            System.out.println(addrBits+": "+value);
        }
    }

    public void printDiskContent() throws MemoryException {
        int diskSize=disk.size();
        int pages=diskSize/pageSize;

        for (int p=0; p<pages; p++) {
            byte[] pageData=disk.readPage(p);  
            System.out.print("PAGE "+p+": ");
            for (int i=0; i<pageSize; i++) {
                System.out.print(pageData[i]);
                if (i<pageSize - 1) {
                    System.out.print(",");
                }
            }
            System.out.println();
        }
    }

    public void writeBackAllPagesToDisk() throws MemoryException {
        for (int page=0; page<numPages; page++) {
            int frame=pageTable[page];
            if (frame!=-1) {
                byte[] data=new byte[pageSize];
                int baseAddr=frame*pageSize;
                for (int i=0; i<pageSize; i++) {
                    data[i]=memory.readByte(baseAddr+i);
                }
                disk.writePage(page, data);
                transferredByteCount+=pageSize; 
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
