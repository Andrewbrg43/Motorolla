package m6800;


public class Memory {

    //total number of addresses
    public static final int SIZE = 0x10000;


    //dummy value to return
    public static final int DUMMY_VALUE = 0x00;

    //four kinds of address
    public static final int UNMAPPED = 0;
    public static final int RAM = 1;
    public static final int ROM = 2;
    public static final int IO = 3;

    //actual byte storage for RAM and ROM, one int per address
    private final int[] data = new int[SIZE];

    //for every address, what kind is it
    private final int[] kind = new int[SIZE];

    //for io addresses, which device maps to them
    private final IODevice[] devices = new IODevice[SIZE];

    //last address of default ram
    public static final int DEFAULT_RAM_END = 0x7FFF;

    //creates memory with default map
    public Memory() {
        mapRam(0x0000, DEFAULT_RAM_END);
    }

    //reads one byte
    public int read(int address) {
        address = address & 0xFFFF;
        switch (kind[address]) {
            case RAM:
            case ROM:
                return data[address];
            case IO:
                return devices[address].read(address) & 0xFF;
            default: // UNMAPPED
                return DUMMY_VALUE;
        }
    }


    //writes one byte
    public void write(int address, int value) {
        address = address & 0xFFFF;
        value = value & 0xFF;
        switch (kind[address]) {
            case RAM:
                data[address] = value;
                break;
            case IO:
                devices[address].write(address, value);
                break;
            default:

                break;
        }
    }


    //reads 16 bit value
    public int read16(int address) {
        int high = read(address);
        int low = read(address + 1);
        return (high << 8) | low;
    }

    // ------------------------------------------------------------------
    // Setting up the memory map
    // ------------------------------------------------------------------


    //makes every address ram, filled with 0
    public final void mapRam(int start, int end) {
        for (int addr = start; addr <= end; addr++) {
            kind[addr] = RAM;
            data[addr] = 0;
            devices[addr] = null;
        }
    }

    /**
     * Hooks a peripheral onto a single address. After this, reads and writes
     * to that address go to the device instead of being stored.
     */
    //hooks peripheral to addresses, the reads and writes to that addresses to go to device instead of storing
    public void mapIO(int address, IODevice device) {
        address = address & 0xFFFF;
        kind[address] = IO;
        data[address] = 0;
        devices[address] = device;
    }

    // ------------------------------------------------------------------
    // Loading (this is what the S19 loader calls)
    // ------------------------------------------------------------------


    //for loading, puts byte in memory from program file, so can write into rom
    public boolean loadByte(int address, int value, boolean asRom) {
        address = address & 0xFFFF;

        if (kind[address] == IO) {
            return false; // never overwrite a peripheral with program data
        }

        if (asRom) {
            kind[address] = ROM;
            data[address] = value & 0xFF;
            return true;
        }

        if (kind[address] == RAM) {
            data[address] = value & 0xFF;
            return true;
        }
        return false;
    }

    //sets ram back to 0
    public void clearRam() {
        for (int addr = 0; addr < SIZE; addr++) {
            if (kind[addr] == RAM) {
                data[addr] = 0;
            }
        }
    }


    //removes rom contents
    public void clearRom() {
        for (int addr = 0; addr < SIZE; addr++) {
            if (kind[addr] == ROM) {
                data[addr] = 0;
                kind[addr] = (addr <= DEFAULT_RAM_END) ? RAM : UNMAPPED;
            }
        }
    }

    //find what kind of address it is
    public int getKind(int address) {
        return kind[address & 0xFFFF];
    }


    //reads byte for displaying
    public int peek(int address) {
        address = address & 0xFFFF;
        if (kind[address] == IO) {
            return -1;
        }
        return read(address);
    }
}
