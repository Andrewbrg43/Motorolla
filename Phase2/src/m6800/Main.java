package m6800;

import java.io.IOException;
import java.util.Scanner;

//does not actually run anything, just takes input and calls methods. can type help for instructions
public class Main {

    //stopping it if it gets in a loop so it doesn't heat my PC up again
    private static final int MAX_RUN_STEPS = 1000000;

    private static Memory memory;
    private static CPU cpu;

    public static void main(String[] args) {
        memory = new Memory();
        cpu = new CPU(memory);      // reset cpu when created
        System.out.println("Type 'help' for the list of commands.");
        printRegisters();

        Scanner input = new Scanner(System.in);
        boolean running = true;

        while (running) {
            System.out.print("\n6800> ");
            if (!input.hasNextLine()) {
                break;                              // end of input
            }
            String line = input.nextLine().trim();
            if (line.length() == 0) {
                continue;
            }

            // split the line, like command and everything after it
            String command;
            String rest;
            int space = line.indexOf(' ');
            if (space == -1) {
                command = line;
                rest = "";
            } else {
                command = line.substring(0, space);
                rest = line.substring(space + 1).trim();
            }
            command = command.toLowerCase();

            try {
                running = runCommand(command, rest);
            } catch (NumberFormatException e) {
                System.out.println("that is not a valid hex number.");
            }
        }

        System.out.println("Ok then I dont want you either");
    }

    //does a command, returns false if user quits, true otherwise
    private static boolean runCommand(String command, String rest) {
        switch (command) {
            case "help":
            case "h":
                printHelp();
                break;

            case "loadram":
                loadFile(rest, false);
                break;

            case "loadrom":
                loadFile(rest, true);
                break;

            case "reset":
                cpu.reset();
                System.out.println("CPU reset. PC loaded from the reset vector at $FFFE/$FFFF.");
                printRegisters();
                break;

            case "step":
            case "s":
                doStep(rest);
                break;

            case "run":
            case "r":
                doRun();
                break;

            case "regs":
            case "reg":
                printRegisters();
                break;

            case "mem":
            case "m":
                doMemoryDump(rest);
                break;

            case "map":
                printMemoryMap();
                break;

            case "poke":
                doPoke(rest);
                break;

            case "setpc":
                cpu.setPC(parseHex(rest));
                cpu.clearStopState();
                System.out.println("PC set.");
                printRegisters();
                break;

            case "irq":
                cpu.requestIRQ();
                System.out.println("IRQ requested (it is only taken if the I flag is 0).");
                break;

            case "nmi":
                cpu.requestNMI();
                System.out.println("NMI requested (it is always taken).");
                break;

            case "clearram":
                memory.clearRam();
                System.out.println("All RAM set to $00.");
                break;

            case "clearrom":
                memory.clearRom();
                System.out.println("ROM removed.");
                break;

            case "ioport":
                memory.mapIO(0x8000, new DemoPort());
                System.out.println("Demo output port mapped at $8000. Try: poke 8000 41");
                break;

            case "quit":
            case "exit":
            case "q":
                return false;

            default:
                System.out.println("Unknown command '" + command + "'. Type 'help'.");
                break;
        }
        return true;
    }

    //loadram and rom, loads file
    private static void loadFile(String filename, boolean asRom) {
        if (filename.length() == 0) {
            System.out.println("Usage: " + (asRom ? "loadrom" : "loadram") + " <file.s19>");
            return;
        }

        try {
            S19Loader.Result result = S19Loader.load(filename, memory, asRom);

            System.out.println("Loaded " + result.bytesLoaded + " bytes as "
                    + (asRom ? "ROM" : "RAM") + " from " + filename);

            if (result.bytesSkipped > 0) {
                System.out.println(String.format(
                        "WARNING: %d bytes could not be stored (first one at $%04X)."
                                + " That address is not RAM.",
                        result.bytesSkipped, result.firstSkippedAddress));
            }

            if (asRom) {
                // reset cpu so it does the reset vector from the ROM.
                cpu.reset();
                System.out.println(String.format(
                        "CPU reset after ROM load. PC = $%04X (from the reset vector).", cpu.getPC()));
            } else if (result.startAddress != -1) {
                // record holds the program's start address, so point PC there.
                cpu.setPC(result.startAddress);
                cpu.clearStopState();
                System.out.println(String.format(
                        "PC set to $%04X (start address from the S9 record).", result.startAddress));
            }
        } catch (IOException e) {
            System.out.println("Could not load file: " + e.getMessage());
        }
    }

    // run n instructions and shows result
    private static void doStep(String rest) {
        int count = 1;
        if (rest.length() > 0) {
            count = Integer.parseInt(rest.trim());      // decimal count
        }

        for (int i = 0; i < count; i++) {
            if (!cpu.canRun()) {
                printStopReason();
                break;
            }
            System.out.println(String.format("Executing opcode $%02X at $%04X",
                    memory.peek(cpu.getPC()), cpu.getPC()));
            cpu.step();
        }
        printRegisters();
    }

    //keeps going until WAI or illegal insruction.
    private static void doRun() {
        int steps = 0;
        while (cpu.canRun() && steps < MAX_RUN_STEPS) {
            cpu.step();
            steps++;
        }
        System.out.println("Ran " + steps + " instruction(s).");

        if (cpu.canRun()) {
            System.out.println("Stopped: reached the limit of " + MAX_RUN_STEPS
                    + " instructions (possible endless loop).");
        } else {
            printStopReason();
        }
        printRegisters();
    }

    //prints a hex dump
    private static void doMemoryDump(String rest) {
        String[] parts = rest.split("\\s+");
        if (rest.length() == 0) {
            System.out.println("Usage: mem <address> [length]    (hex numbers)");
            return;
        }

        int start = parseHex(parts[0]);
        int length = 64;
        if (parts.length > 1) {
            length = parseHex(parts[1]);
        }

        for (int row = 0; row < length; row += 16) {
            StringBuilder line = new StringBuilder();
            line.append(String.format("%04X:", (start + row) & 0xFFFF));

            for (int col = 0; col < 16 && (row + col) < length; col++) {
                int address = (start + row + col) & 0xFFFF;
                int value = memory.peek(address);
                if (value == -1) {
                    line.append(" IO");                // IO addresses are not read
                } else {
                    line.append(String.format(" %02X", value));
                }
            }
            System.out.println(line);
        }
    }

    // a write, ignored by ROM, handled by IO
    private static void doPoke(String rest) {
        String[] parts = rest.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Usage: poke <address> <value>    (hex numbers)");
            return;
        }
        int address = parseHex(parts[0]);
        int value = parseHex(parts[1]);

        int kind = memory.getKind(address);
        memory.write(address, value);

        if (kind == Memory.ROM || kind == Memory.UNMAPPED) {
            System.out.println(String.format("Nothing changed: $%04X is not writable (%s).",
                    address, kind == Memory.ROM ? "ROM" : "unmapped"));
        } else {
            System.out.println(String.format("Wrote $%02X to $%04X.", value & 0xFF, address));
        }
    }



    //show registers and condition codes
    private static void printRegisters() {
        System.out.println(String.format("  A  = $%02X      B  = $%02X", cpu.getA(), cpu.getB()));
        System.out.println(String.format("  X  = $%04X    SP = $%04X    PC = $%04X",
                cpu.getX(), cpu.getSP(), cpu.getPC()));
        System.out.println(String.format("  CCR = $%02X  =  1 1 H I N Z V C", cpu.getCCR()));
        System.out.println(String.format("                  1 1 %d %d %d %d %d %d",
                bit(cpu.getH()), bit(cpu.getI()), bit(cpu.getN()),
                bit(cpu.getZ()), bit(cpu.getV()), bit(cpu.getC())));
        System.out.println("  Instructions run since reset: " + cpu.getInstructionCount());
    }

    //says if cpu is halted
    private static void printStopReason() {
        if (cpu.isHalted()) {
            System.out.println("CPU HALTED: " + cpu.getHaltReason());
        } else if (cpu.isWaiting()) {
            System.out.println("CPU is in WAI (waiting for interrupt). "
                    + "Use 'irq' or 'nmi' to wake it, or 'reset' / 'setpc'.");
        }
    }

   // prints addreses that are ram, rom etc
    private static void printMemoryMap() {
        int start = 0;
        int currentKind = memory.getKind(0);

        for (int addr = 1; addr <= 0x10000; addr++) {
            // At addr == 0x10000 we are past the end, so close the last range.
            if (addr == 0x10000 || memory.getKind(addr) != currentKind) {
                System.out.println(String.format("  $%04X - $%04X  %s",
                        start, addr - 1, kindName(currentKind)));
                if (addr < 0x10000) {
                    start = addr;
                    currentKind = memory.getKind(addr);
                }
            }
        }
    }

    private static String kindName(int kind) {
        switch (kind) {
            case Memory.RAM: return "RAM";
            case Memory.ROM: return "ROM";
            case Memory.IO:  return "IO";
            default:         return "unmapped (reads return $" +
                    String.format("%02X", Memory.DUMMY_VALUE) + ")";
        }
    }

    // printin commands if help command is run
    private static void printHelp() {
        System.out.println("Commands (all addresses and values are HEX, $ or 0x prefix is optional):");
        System.out.println("  loadram <file>      load an S19 file into RAM (sets PC from its S9 record)");
        System.out.println("  loadrom <file>      load an S19 file as ROM (then resets the CPU)");
        System.out.println("  reset               reset the CPU (PC = reset vector at $FFFE/$FFFF)");
        System.out.println("  step [n]            run 1 instruction");
        System.out.println("  run                 run until WAI, an illegal opcode, or 1,000,000 steps");
        System.out.println("  regs                show A, B, X, SP, PC and the condition codes");
        System.out.println("  mem <addr> [len]    hex dump of memory (default length $40)");
        System.out.println("  map                 show which address ranges are RAM / ROM / IO");
        System.out.println("  poke <addr> <val>   write one byte like the CPU would");
        System.out.println("  setpc <addr>        set the program counter by hand");
        System.out.println("  irq / nmi           request an interrupt");
        System.out.println("  clearram            set all RAM to 0");
        System.out.println("  clearrom            remove all ROM contents");
        System.out.println("  ioport              map an output port at $8000");
        System.out.println("  help                take a wild guess");
        System.out.println("  quit                exit");

    }



    private static int bit(boolean flag) {
        return flag ? 1 : 0;
    }

    // converts hex text into numbers
    private static int parseHex(String text) {
        text = text.trim();
        if (text.startsWith("$")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        int value = Integer.parseInt(text, 16);
        if (value < 0 || value > 0xFFFF) {
            throw new NumberFormatException("out of range");
        }
        return value;
    }
}
