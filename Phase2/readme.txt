=====================================================================
 COSC3P92 - Phase 2 - Motorola 6800 Simulator
 Commands and Test Instructions
 Andrew Brgan (qx22rg), Bryson Sulkowski (bs09ps), Christian Cortina (cc21lv)
=====================================================================


---------------------------------------------------------------------
 PART 1 - WHAT IS IN THE PROJECT
---------------------------------------------------------------------

 src/m6800/CPU.java         the processor (registers, fetch/decode/execute,
                            all instructions, interrupts)
 src/m6800/Memory.java      the 64K address space (RAM / ROM / IO / unmapped)
 src/m6800/IODevice.java    interface that peripherals implement
 src/m6800/DemoPort.java    small example output-only peripheral
 src/m6800/S19Loader.java   reads S19 files into memory
 src/m6800/Main.java        the text interface (this is what you run)

 samples/sample_rom.s19     ROM sample: vector table. RESET jumps to $0000.
                            IRQ, SWI and NMI all point at one RTI ($F003).
 samples/sample_ram.s19     RAM sample: adds 10+9+...+1 and stores the
                            answer (55 = $37) at address $0020, then WAI.
 tests/6800b1.s19           the provided test file
 samples/*.asm, tests/*.asm the assembly source for the files above

 build.sh / run.sh          optional helper scripts (Mac/Linux). Not needed
                            in IntelliJ.


---------------------------------------------------------------------
 PART 2 - HOW TO RUN THE PROGRAM
---------------------------------------------------------------------

 IntelliJ
   1. File > New > Project from Existing Sources, pick the project folder.
   2. Right-click the "src" folder > Mark Directory as > Sources Root.
   3. Open src/m6800/Main.java and run it (green arrow next to main).
   4. IMPORTANT: file names like samples/sample_ram.s19 are relative to the
      "working directory". In Run > Edit Configurations, set the working
      directory to the project folder (the folder that contains "samples"
      and "tests"). If you see "Could not load file", this is why. You can
      also type the full path, e.g.  loadram C:\Phase 2\tests\6800b1.s19

 Command line (alternative)
   From the project folder:
      mkdir out
      javac -d out src/m6800/*.java
      java -cp out m6800.Main
   (On Mac/Linux, ./run.sh does the same thing.)

 When it starts you should see:

      === Motorola 6800 Simulator (Phase 2) ===
      Type 'help' for the list of commands.
        A  = $00      B  = $00
        X  = $0000    SP = $0000    PC = $0000
        CCR = $D0  =  1 1 H I N Z V C
                        1 1 0 1 0 0 0 0
        Instructions run since reset: 0

 followed by the prompt   6800>   where you type commands.

 READING THE REGISTER DISPLAY (printed by regs, step, run, reset):
   A, B  = accumulators (8 bit)        X  = index register (16 bit)
   SP    = stack pointer (16 bit)      PC = program counter (16 bit)
   CCR   = condition code register. The second row shows the bits
           1 1 H I N Z V C   (H half carry, I interrupt mask, N negative,
           Z zero, V overflow, C carry). The top two bits are always 1.
   After a reset the I bit is 1, which is why CCR starts as $D0.


---------------------------------------------------------------------
 PART 3 - THE COMMANDS
---------------------------------------------------------------------

 All addresses and values are in HEX. A "$" or "0x" in front is optional
 (poke 20 FF, poke $20 $FF and poke 0x20 0xFF all work). The only decimal
 number is the count in "step n". Command names are not case sensitive.

 loadram <file>
     Loads an S19 file into RAM. Only addresses that are RAM can be filled;
     any bytes aimed at ROM/unmapped/IO addresses are skipped and a warning
     is printed. If the file has an S9 record, the program counter is set
     to the start address in it and any WAI/halt state is cleared.
     Example:  loadram tests/6800b1.s19

 loadrom <file>
     Loads an S19 file as ROM. Every address in the file becomes read-only
     (CPU writes to it are ignored). The CPU is then reset, so PC is
     loaded from the reset vector at $FFFE/$FFFF.
     Example:  loadrom samples/sample_rom.s19

 reset
     Resets the CPU: PC = the 16-bit value at $FFFE/$FFFF, I flag = 1,
     and A, B, X, SP, and the other flags = 0. Memory is not touched.
     If nothing is loaded at the vector, PC becomes $0000.

 step [n]            (alias: s)
     Runs one instruction, or n instructions (n is decimal), then shows the
     registers. Before each instruction it prints the opcode about to run.
     If the CPU is stopped (WAI or illegal opcode) it says why.
     Examples:  step      step 5

 run                 (alias: r)
     Runs until the CPU executes WAI, hits an illegal opcode, or reaches
     1,000,000 instructions (protection against endless loops). Prints the
     number of instructions run and the registers.

 regs                (alias: reg)
     Shows A, B, X, SP, PC and the condition codes. Changes nothing.

 mem <addr> [len]    (alias: m)
     Hex dump of memory, 16 bytes per row. len is the number of bytes
     (hex); default is $40 (64 bytes). IO addresses show as "IO" because
     reading them could change a peripheral.
     Examples:  mem 0    mem 20 1    mem FFF8 8

 map
     Lists which address ranges are RAM, ROM, IO or unmapped.

 poke <addr> <value>
     Writes one byte the way the CPU would. RAM is changed, ROM and
     unmapped addresses ignore it (and it tells you), IO addresses are
     passed to the device. Handy for typing in a small program by hand.
     Example:  poke 30 AB

 setpc <addr>
     Sets the program counter by hand and clears any WAI/halt state.
     Example:  setpc 0

 irq
     Requests a maskable interrupt. It is only taken if the I flag is 0.
     (Until then it stays pending.)

 nmi
     Requests a non-maskable interrupt. Always taken, ignores the I flag.
     (The interrupt is taken on the next step/run.)

 clearram
     Sets every RAM byte to $00. ROM and IO are not touched.

 clearrom
     Removes all ROM. Those addresses go back to unmapped (or RAM if they
     were inside $0000-$7FFF).

 ioport
     Maps a demo output-only port at address $8000. Writes to $8000 print
     "[DemoPort $8000] CPU wrote $xx"; reads return $FF.

 help (h, ?)         shows the command list
 quit (q, exit)      leaves the program


---------------------------------------------------------------------
 PART 4 - MEMORY MAP USED
---------------------------------------------------------------------

   $0000 - $7FFF   RAM (32K) - programs, direct page, stack
   $8000 - $FFFF   unmapped by default. Reads return the dummy value $00,
                   writes are ignored. ROM (from loadrom) and IO ports
                   (from ioport) get placed here.
   $FFF8-$FFFF     interrupt vectors (supplied by the ROM file):
                   $FFF8 IRQ   $FFFA SWI   $FFFC NMI   $FFFE RESET

 Because unmapped memory reads $00, starting the simulator with NO ROM
 loaded makes the reset vector read $0000, so the CPU starts at address 0.


---------------------------------------------------------------------
 PART 5 - TESTS (follow in order; each starts from a fresh run)
---------------------------------------------------------------------

 Start a fresh run of the program (stop and start it again) before each
 test unless told otherwise. In the expected output below, the "6800>"
 prompt and blank lines are left out. "(registers)" means the usual
 4-line register display; only the values that matter are shown.


 TEST 1 - Help and quit                       (UI: basic controls)
 ---------------------------------------------------------------
 Type:      help
 Expected:  The command list, ending with the "quit  exit" line.
 Type:      quit
 Expected:  Goodbye.   (and the program ends)


 TEST 2 - Load an S19 as RAM and run it       (UI: load S19, run)
 ---------------------------------------------------------------
 This is the provided test file:  LDAA #$04 / BRA over a NOP and WAI /
 STAA $B1 / WAI.
 Type:      loadram tests/6800b1.s19
 Expected:  Loaded 16 bytes as RAM from tests/6800b1.s19
            PC set to $0000 (start address from the S9 record).
 Type:      run
 Expected:  Ran 4 instruction(s).
            CPU is in WAI (waiting for interrupt). Use 'irq' or 'nmi' ...
              A  = $04      B  = $00
              X  = $0000    SP = $FFF9    PC = $0009
              CCR = $D0 ...
              Instructions run since reset: 4
 Type:      mem b0 4
 Expected:  00B0: FF 04 FF FF
 Why:       The 4 instructions are LDAA, BRA, STAA, WAI. The BRA skips the
            NOP and the first WAI. STAA stored $04 at $00B1 (the second
            byte shown). WAI pushes 7 bytes on the stack; SP started at 0,
            so it wraps down to $FFF9. PC = $0009 is the address after the
            WAI.
 Type:      mem a0 3
 Expected:  00A0: 04 03 05     (data defined by the test file)


 TEST 3 - Step through a program              (UI: step)
 ---------------------------------------------------------------
 Type:      loadram tests/6800b1.s19
 Type:      step
 Expected:  Executing opcode $86 at $0000
            A = $04   PC = $0002   Instructions run since reset: 1
 Type:      step
 Expected:  Executing opcode $20 at $0002
            PC = $0006      (BRA jumped over the NOP and WAI)
 Type:      step
 Expected:  Executing opcode $97 at $0006
            PC = $0008
 Type:      step
 Expected:  Executing opcode $3E at $0008
            SP = $FFF9   PC = $0009   Instructions run since reset: 4
 Type:      step
 Expected:  CPU is in WAI (waiting for interrupt). Use 'irq' or 'nmi' to
            wake it, or 'reset' / 'setpc'.      (nothing more executes)
 Also try:  "step 3" after loading, which runs 3 instructions at once
            and prints the registers once at the end.


 TEST 4 - Inspect the status codes (condition codes)   (UI: status codes)
 ---------------------------------------------------------------
 A program is typed in by hand with poke. LDAA #$80 is the bytes 86 80.
 Type:      poke 0 86
 Type:      poke 1 80
 Type:      setpc 0
 Type:      step
 Expected:  Executing opcode $86 at $0000
              A  = $80      B  = $00
              X  = $0000    SP = $0000    PC = $0002
              CCR = $D8  =  1 1 H I N Z V C
                              1 1 0 1 1 0 0 0
 Why:       Loading $80 makes the result negative, so N = 1 (CCR $D8).

 Now restart the program, then:
 Type:      poke 0 86
 Type:      poke 1 00
 Type:      setpc 0
 Type:      step
 Expected:  A = $00 and CCR = $D4 (bits: 1 1 0 1 0 1 0 0 -> Z = 1).

 Restart again, then use SEC (0D), SEV (0B), CLI (0E):
 Type:      poke 0 0D
 Type:      poke 1 0B
 Type:      poke 2 0E
 Type:      setpc 0
 Type:      step 3
 Expected:  CCR = $C3  (bits 1 1 0 0 0 0 1 1)
 Why:       SEC sets C, SEV sets V, CLI clears the I bit that reset set.
            (Without CLI the value would be $D3.)


 TEST 5 - Inspect and change arbitrary RAM    (UI: inspect RAM)
 ---------------------------------------------------------------
 Type:      poke 30 AB
 Expected:  Wrote $AB to $0030.
 Type:      mem 30 2
 Expected:  0030: AB 00
 Type:      mem 0 20
 Expected:  Two rows of 16 bytes starting at 0000: and 0010:


 TEST 6 - ROM and RAM together                (UI: load as ROM / RAM)
 ---------------------------------------------------------------
 Type:      loadrom samples/sample_rom.s19
 Expected:  Loaded 12 bytes as ROM from samples/sample_rom.s19
            CPU reset after ROM load. PC = $F000 (from the reset vector).
 Type:      map
 Expected:    $0000 - $7FFF  RAM
              $8000 - $EFFF  unmapped (reads return $00)
              $F000 - $F003  ROM
              $F004 - $FFF7  unmapped (reads return $00)
              $FFF8 - $FFFF  ROM
 Type:      loadram samples/sample_ram.s19
 Expected:  Loaded 11 bytes as RAM from samples/sample_ram.s19
            PC set to $0000 (start address from the S9 record).
 Type:      reset
 Expected:  CPU reset. PC loaded from the reset vector at $FFFE/$FFFF.
            PC = $F000, A = B = X = SP = $0000
            (the reset vector in the ROM points at $F000)
 Type:      step 4
 Expected:  Executing opcode $7E at $F000     (JMP $0000, in the ROM)
            Executing opcode $86 at $0000     (LDAA #0, in RAM)
            Executing opcode $C6 at $0002     (LDAB #10)
            Executing opcode $1B at $0004     (ABA)
            A = $0A   B = $0A   PC = $0005
 Type:      reset
 Type:      run
 Expected:  Ran 35 instruction(s).
            CPU is in WAI ...
            A = $37   B = $00   SP = $FFF9   PC = $000B
 Type:      mem 20 1
 Expected:  0020: 37          (55 decimal = the sum 10+9+...+1)
 Why:       The reset vector in ROM jumps to the RAM program, which loops
            adding B to A (10 times), stores A at $0020 and stops at WAI.


 TEST 7 - ROM cannot be overwritten           (core: ROM)
 ---------------------------------------------------------------
 Type:      loadrom samples/sample_rom.s19
 Type:      mem FFF8 8
 Expected:  FFF8: F0 03 F0 03 F0 03 F0 00      (the vector table)
 Type:      poke FFF8 99
 Expected:  Nothing changed: $FFF8 is not writable (ROM).
 Type:      mem FFF8 8
 Expected:  FFF8: F0 03 F0 03 F0 03 F0 00      (unchanged)
 Also:      loadram samples/sample_rom.s19 on a fresh start (no ROM loaded)
 Expected:  Loaded 0 bytes as RAM from samples/sample_rom.s19
            WARNING: 12 bytes could not be stored (first one at $F000).
            That address is not RAM.
            (a RAM load is not allowed to put data into non-RAM addresses)


 TEST 8 - Unpopulated addresses return a dummy value   (core: memory)
 ---------------------------------------------------------------
 Type:      mem 9000 4
 Expected:  9000: 00 00 00 00
 Type:      poke 9000 55
 Expected:  Nothing changed: $9000 is not writable (unmapped).
 Type:      mem 9000 4
 Expected:  9000: 00 00 00 00


 TEST 9 - Clear memory                        (UI: clear memory)
 ---------------------------------------------------------------
 (Continue from Test 7 so a ROM is loaded.)
 Type:      poke 30 AB
 Type:      clearram
 Expected:  All RAM set to $00.
 Type:      mem 30 2
 Expected:  0030: 00 00
 Type:      clearrom
 Expected:  ROM removed.
 Type:      map
 Expected:    $0000 - $7FFF  RAM
              $8000 - $FFFF  unmapped (reads return $00)


 TEST 10 - IO port hook                       (core: IO ports)
 ---------------------------------------------------------------
 Type:      ioport
 Expected:  Demo output port mapped at $8000. Try: poke 8000 41
 Type:      map
 Expected:    $0000 - $7FFF  RAM
              $8000 - $8000  IO
              $8001 - $FFFF  unmapped (reads return $00)
 Type:      poke 8000 41
 Expected:     [DemoPort $8000] CPU wrote $41
            Wrote $41 to $8000.
 Type:      mem 8000 2
 Expected:  8000: IO 00
 Why:       A write to $8000 is handed to the device instead of being
            stored, and the dump does not read the device (shows "IO").
            The same thing happens when the CPU runs STAA $8000.


 TEST 11 - Interrupts and WAI
 ---------------------------------------------------------------
 A tiny program is typed in:  LDS #$01FF / CLI / BRA * (loop forever).
 Type:      loadrom samples/sample_rom.s19
 Type:      poke 0 8E
 Type:      poke 1 01
 Type:      poke 2 FF
 Type:      poke 3 0E
 Type:      poke 4 20
 Type:      poke 5 FE
 Type:      setpc 0
 Type:      step 3
 Expected:  Executing opcode $8E at $0000
            Executing opcode $0E at $0003
            Executing opcode $20 at $0004
            SP = $01FF   PC = $0004   CCR = $C0   (I cleared by CLI)
 Type:      irq
 Expected:  IRQ requested (it is only taken if the I flag is 0).
 Type:      step
 Expected:  (the "Executing opcode $20 at $0004" line is printed, but the
            interrupt is taken instead of the instruction)
            SP = $01F8   PC = $F003   CCR = $D0
 Why:       The CPU pushed 7 bytes (PC, X, A, B, CCR), set the I bit, and
            loaded PC from the IRQ vector ($FFF8) = $F003, the RTI.
 Type:      step
 Expected:  Executing opcode $3B at $F003      (RTI)
            SP = $01FF   PC = $0004   CCR = $C0
 Why:       RTI pulled everything back: back in the loop, I cleared again.

 NMI ignores the I flag (restart, then):
 Type:      loadrom samples/sample_rom.s19
 Type:      setpc 0
 Type:      nmi
 Type:      step
 Expected:  PC = $F003 and SP = $FFF9 even though I = 1 (CCR $D0).
            Instructions run since reset: 0  (the interrupt took the step).

 WAI: seen in Tests 2, 3 and 6. The CPU stops with the registers stacked.
 To wake a stopped CPU: set up a vector, then type irq (with I = 0) or nmi.


 TEST 12 - Illegal opcode and bad input
 ---------------------------------------------------------------
 Type:      poke 0 02
 Type:      setpc 0
 Type:      step
 Expected:  Executing opcode $02 at $0000
            (registers; PC = $0001)
 Type:      step
 Expected:  CPU HALTED: Illegal opcode $02 at $0000
 Type:      loadram nofile.s19
 Expected:  Could not load file: nofile.s19 (No such file or directory)
            (the text in brackets depends on the operating system)
 Type:      mem zz
 Expected:  Error: that is not a valid hex number.
 Type:      foo
 Expected:  Unknown command 'foo'. Type 'help'.


 TEST 13 - A program that sets up its own stack  (optional)
 ---------------------------------------------------------------
 The provided test files never set SP, so on WAI the 7 stack bytes start at
 address $0000 and overwrite the first byte of the program (this is also
 what a real 6800 would do, since RESET does not set SP).
 To see the effect: load the sample ROM and RAM, type  reset  and  run
 (works, A = $37). Then type  reset  and  run  AGAIN without reloading the RAM.
 Expected:  the CPU halts with "Illegal opcode $00 at $0001", because byte
            $0000 was overwritten by the stacked PC.
 Fix:       type  loadram samples/sample_ram.s19  before running it again.


---------------------------------------------------------------------
 PART 6 - TESTING WITH YOUR OWN ASSEMBLY PROGRAMS
---------------------------------------------------------------------

 1. Build the assembler from https://github.com/JimInCA/motorola-6800-assembler
    (run "make"; the 6800 version is bin/as0).
 2. Assemble:   as0 myprogram.asm        (produces myprogram.s19)
    If the assembler complains about "Unrecognized Mnemonic" on a file
    created in Windows, the file has Windows (CRLF) line endings; save it
    with Unix (LF) line endings and try again.
 3. In the simulator:   loadram myprogram.s19    then   run   (or step)
 4. End every test program with WAI so "run" stops there, then use regs and
    mem to check the results.
 5. A ROM file must define the interrupt vectors at $FFF8-$FFFF
    (see samples/sample_rom.asm for an example).


---------------------------------------------------------------------
 PART 7 - NOTES
---------------------------------------------------------------------

 - Clock cycles are not simulated; one step = one whole instruction.
 - S19 checksums are ignored.
 - RESET sets A, B, X, SP and flags to 0 (the real chip leaves them
   undefined) and sets the I flag.
 - Loading a RAM file does not reset the registers or the instruction
   count; use "reset" if you want a clean start.
 - Unused opcodes halt the CPU with a message (see Test 12).
