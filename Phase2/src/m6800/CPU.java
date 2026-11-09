package m6800;


public class CPU {

    // The four addressing modes
    private static final int IMM = 0;  // immediate
    private static final int DIR = 1;  // direct
    private static final int IDX = 2;  // indexed
    private static final int EXT = 3;  // extended

    // interrupt vector addresses
    private static final int VECTOR_IRQ   = 0xFFF8;
    private static final int VECTOR_SWI   = 0xFFFA;
    private static final int VECTOR_NMI   = 0xFFFC;
    private static final int VECTOR_RESET = 0xFFFE;

    private final Memory memory;

    // visible registers
    private int a;       // accumulator A, always kept in 0 to 255
    private int b;       // accumulator B, always kept in 0 to 255
    private int x;       // index register, always kept in 0 to 65535
    private int sp;      // stack pointer,  always kept in 0 to 65535
    private int pc;      // program counter, always kept in 0 to 65535

    // condition code flags
    private boolean flagH;
    private boolean flagI;
    private boolean flagN;
    private boolean flagZ;
    private boolean flagV;
    private boolean flagC;

    // processor state
    private boolean waiting = false;   // true after wai, until interrupt
    private boolean halted = false;    // true after an illegal opcode
    private String haltReason = "";
    private boolean irqPending = false;
    private boolean nmiPending = false;
    private long instructionCount = 0;

    // Creates a cpu connected to the memory and does reset
    public CPU(Memory memory) {
        this.memory = memory;
        reset();
    }


    // reset, instead of leaving them undefined, just set to 0/false so its consistent
    public final void reset() {
        a = 0;
        b = 0;
        x = 0;
        sp = 0;
        flagH = false;
        flagN = false;
        flagZ = false;
        flagV = false;
        flagC = false;
        flagI = true;               // sets the interrupt mask
        waiting = false;
        halted = false;
        haltReason = "";
        irqPending = false;
        nmiPending = false;
        instructionCount = 0;
        pc = memory.read16(VECTOR_RESET);
    }


    // runs one instruction/interrupt, if interrupt then do it, if halted then do nothing, otherwise do opcode
    public void step() {
        if (halted) {
            return;
        }
        if (checkInterrupts()) {
            return;
        }
        if (waiting) {
            return;     // stay until interrupt
        }

        int opcode = fetch8();                  // fetch
        instructionCount++;
        execute(opcode);                        // decode, execute
    }

    // sends opcode to right group
    private void execute(int op) {
        if (op >= 0x80) {
            executeTwoOperand(op);
        } else if (op >= 0x40) {
            executeSingleOperand(op);
        } else {
            executeMisc(op);
        }
    }



    //request maskable interrupt
    public void requestIRQ() {
        irqPending = true;
    }

    //request non maskable interrupt
    public void requestNMI() {
        nmiPending = true;
    }

    //looks for interrupt and starts it if allowed, follows rules for nmi and irq, returns true if interrupt was don
    private boolean checkInterrupts() {
        if (nmiPending) {
            nmiPending = false;
            takeInterrupt(VECTOR_NMI);
            return true;
        }
        if (irqPending && !flagI) {
            irqPending = false;
            takeInterrupt(VECTOR_IRQ);
            return true;
        }
        return false;
    }

    //hardware interrupt, set i, load pc, don't push registers again if wai
    private void takeInterrupt(int vector) {
        if (!waiting) {
            pushAllRegisters();
        }
        waiting = false;
        flagI = true;
        pc = memory.read16(vector);
    }

    //pushes pc, x, a, b, ccr 7 bytes. order matters
    private void pushAllRegisters() {
        push8(pc & 0xFF);
        push8(pc >> 8);
        push8(x & 0xFF);
        push8(x >> 8);
        push8(a);
        push8(b);
        push8(getCCR());
    }

    //reads byte at pc, moves to next
    private int fetch8() {
        int value = memory.read(pc);
        pc = (pc + 1) & 0xFFFF;
        return value;
    }

    //reads 2 bytes at pc, moves past both
    private int fetch16() {
        int high = fetch8();
        int low = fetch8();
        return (high << 8) | low;
    }

    //pushes byte, store at sp, lower sp
    private void push8(int value) {
        memory.write(sp, value);
        sp = (sp - 1) & 0xFFFF;
    }

    //opposite of last, pulls byte, increase sp
    private int pull8() {
        sp = (sp + 1) & 0xFFFF;
        return memory.read(sp);
    }

    //pushes 16 bit address
    private void push16(int value) {
        push8(value & 0xFF);
        push8((value >> 8) & 0xFF);
    }

    //pulls 16 bit address
    private int pull16() {
        int high = pull8();
        int low = pull8();
        return (high << 8) | low;
    }

    //if 8 bit use these flags
    private void setNZ8(int value) {
        flagN = (value & 0x80) != 0;
        flagZ = (value & 0xFF) == 0;
    }

    //if 16 bit use these flags
    private void setNZ16(int value) {
        flagN = (value & 0x8000) != 0;
        flagZ = (value & 0xFFFF) == 0;
    }

    //finds where oprand is and advances pc, returns operand address
    private int operandAddress(int mode) {
        switch (mode) {
            case IMM:
                // immediate: the operand is after the opcode, so address is pc
                int addr = pc;
                pc = (pc + 1) & 0xFFFF;
                return addr;
            case DIR:
                // direct: next byte is address
                return fetch8();
            case IDX:
                // indexed: x plus the next byte
                return (x + fetch8()) & 0xFFFF;
            default:
                // extended: next 2 bytes are address.
                return fetch16();
        }
    }


    //same as before operandAddress, but skips 2 bytes as this is for 16 bit operands
    private int operandAddress16(int mode) {
        if (mode == IMM) {
            int addr = pc;
            pc = (pc + 2) & 0xFFFF;
            return addr;
        }
        return operandAddress(mode);
    }

    //for illegal opcodes
    private void illegal(int opcode) {
        halted = true;
        haltReason = String.format("Illegal opcode $%02X at $%04X",
                opcode, (pc - 1) & 0xFFFF);
    }

    //8 bit addition
    private int add8(int p, int q, int carryIn) {
        int sum = p + q + carryIn;
        int result = sum & 0xFF;
        flagH = ((p & 0x0F) + (q & 0x0F) + carryIn) > 0x0F;
        flagC = sum > 0xFF;
        flagV = ((p ^ result) & (q ^ result) & 0x80) != 0;
        setNZ8(result);
        return result;
    }


    //8 bit subtraction
    private int sub8(int p, int q, int carryIn) {
        int diff = p - q - carryIn;
        int result = diff & 0xFF;
        flagC = diff < 0;
        flagV = ((p ^ q) & (p ^ result) & 0x80) != 0;
        setNZ8(result);
        return result;
    }


    //decimal adjust a
    private void daa() {
        int highNibble = a & 0xF0;
        int lowNibble = a & 0x0F;
        int correction = 0;

        if (lowNibble > 0x09 || flagH) {
            correction |= 0x06;
        }
        if (highNibble > 0x80 && lowNibble > 0x09) {
            correction |= 0x60;
        }
        if (highNibble > 0x90 || flagC) {
            correction |= 0x60;
        }

        int total = a + correction;
        a = total & 0xFF;
        setNZ8(a);
        flagV = false;
        if (total > 0xFF) {
            flagC = true;
        }
    }


    //opcodes 0x00- 0x3f
    private void executeMisc(int op) {
        switch (op) { //theres probably a better way of doing this then a giant switch, but i don't know it


            case 0x01: break;                                   // NOP
            case 0x06: setCCR(a); break;                        // TAP
            case 0x07: a = getCCR(); break;                     // TPA
            case 0x0A: flagV = false; break;                    // CLV
            case 0x0B: flagV = true; break;                     // SEV
            case 0x0C: flagC = false; break;                    // CLC
            case 0x0D: flagC = true; break;                     // SEC
            case 0x0E: flagI = false; break;                    // CLI
            case 0x0F: flagI = true; break;                     // SEI

            //index register/stack pointer
            case 0x08:                                          // INX
                x = (x + 1) & 0xFFFF;
                flagZ = (x == 0);
                break;
            case 0x09:                                          // DEX
                x = (x - 1) & 0xFFFF;
                flagZ = (x == 0);
                break;
            case 0x30: x = (sp + 1) & 0xFFFF; break;            // TSX
            case 0x31: sp = (sp + 1) & 0xFFFF; break;           // INS
            case 0x34: sp = (sp - 1) & 0xFFFF; break;           // DES
            case 0x35: sp = (x - 1) & 0xFFFF; break;            // TXS

            // between accumulators
            case 0x10: a = sub8(a, b, 0); break;                // SBA
            case 0x11: sub8(a, b, 0); break;                    // CBA
            case 0x16:                                          // TAB
                b = a;
                setNZ8(b);
                flagV = false;
                break;
            case 0x17:                                          // TBA
                a = b;
                setNZ8(a);
                flagV = false;
                break;
            case 0x19: daa(); break;                            // DAA
            case 0x1B: a = add8(a, b, 0); break;                // ABA

            //stack push/pulls
            case 0x32: a = pull8(); break;                      // PULA
            case 0x33: b = pull8(); break;                      // PULB
            case 0x36: push8(a); break;                         // PSHA
            case 0x37: push8(b); break;                         // PSHB

            //subroutine/interrupt returns
            case 0x39:                                          // RTS
                pc = pull16();
                break;
            case 0x3B:                                          // RTI
                //pull in reverse order of pushAllRegisters.
                setCCR(pull8());
                b = pull8();
                a = pull8();
                int xHigh = pull8();
                int xLow = pull8();
                x = (xHigh << 8) | xLow;
                pc = pull16();
                break;

            //wai and swi
            case 0x3E:                                          // WAI
                // push everything, stop until interrupt
                pushAllRegisters();
                waiting = true;
                break;
            case 0x3F:                                          // SWI
                pushAllRegisters();
                flagI = true;
                pc = memory.read16(VECTOR_SWI);
                break;

            default:
                // 0x20 - 0x2F are the branches except the BRN one
                if (op >= 0x20 && op <= 0x2F && op != 0x21) {
                    branch(op);
                } else {
                    illegal(op);
                }
                break;
        }
    }


    //branches using relative addressing, byte after opcode is offset added to address of next instruction
    private void branch(int op) {
        int offset = (byte) fetch8();
        if (branchCondition(op)) {
            pc = (pc + offset) & 0xFFFF;
        }
    }

    private boolean branchCondition(int op) {
        switch (op) {
            case 0x20: return true;                              // BRA  always
            case 0x22: return !flagC && !flagZ;                  // BHI  higher
            case 0x23: return flagC || flagZ;                    // BLS  lower or same
            case 0x24: return !flagC;                            // BCC  carry clear
            case 0x25: return flagC;                             // BCS  carry set
            case 0x26: return !flagZ;                            // BNE  not equal
            case 0x27: return flagZ;                             // BEQ  equal
            case 0x28: return !flagV;                            // BVC  overflow clear
            case 0x29: return flagV;                             // BVS  overflow set
            case 0x2A: return !flagN;                            // BPL  plus
            case 0x2B: return flagN;                             // BMI  minus
            case 0x2C: return !(flagN ^ flagV);                  // BGE  >= (signed)
            case 0x2D: return flagN ^ flagV;                     // BLT  <  (signed)
            case 0x2E: return !flagZ && !(flagN ^ flagV);        // BGT  >  (signed)
            default:   return flagZ || (flagN ^ flagV);          // BLE  <= (signed), op 0x2F
        }
    }

    //opcodes 0x40 - 0x7F
    private void executeSingleOperand(int op) {
        int high = op >> 4;       // 4, 5, 6, 7
        int low = op & 0x0F;

        // jmp doesnt read/write the operand
        if (low == 0x0E) {
            if (high == 6) {
                pc = (x + fetch8()) & 0xFFFF;    // jmp indexed
            } else if (high == 7) {
                pc = fetch16();                  // jmp extended
            } else {
                illegal(op);
            }
            return;
        }

        if (low == 0x01 || low == 0x02 || low == 0x05 || low == 0x0B) {
            illegal(op);
            return;
        }

        if (high == 4) {                                   // accumulator A
            int result = singleOperandMath(low, a);
            if (low != 0x0D) {
                a = result;
            }
        } else if (high == 5) {                            // accumulator B
            int result = singleOperandMath(low, b);
            if (low != 0x0D) {
                b = result;
            }
        } else {                                           // memory
            int addr = (high == 6) ? operandAddress(IDX) : operandAddress(EXT);
            int value = (low == 0x0F) ? 0 : memory.read(addr);
            int result = singleOperandMath(low, value);
            if (low != 0x0D) {
                memory.write(addr, result);
            }
        }
    }

    //the actual math and sets flags
    private int singleOperandMath(int low, int v) {
        int r;
        switch (low) {

            case 0x00: // NEG
                r = (0 - v) & 0xFF;
                flagV = (r == 0x80);       // 0x80 negates to itself
                flagC = (r != 0);          // borrow happens unless v was 0
                setNZ8(r);
                return r;

            case 0x03: // COM
                r = (~v) & 0xFF;
                setNZ8(r);
                flagV = false;
                flagC = true;              // com always sets C
                return r;

            case 0x04: // LSR
                flagC = (v & 0x01) != 0;
                r = v >> 1;
                setNZ8(r);                 // n is always 0
                flagV = flagN ^ flagC;
                return r;

            case 0x06: // ROR
                r = (v >> 1) | (flagC ? 0x80 : 0x00);
                flagC = (v & 0x01) != 0;
                setNZ8(r);
                flagV = flagN ^ flagC;
                return r;

            case 0x07: // ASR
                flagC = (v & 0x01) != 0;
                r = (v >> 1) | (v & 0x80);
                setNZ8(r);
                flagV = flagN ^ flagC;
                return r;

            case 0x08: // ASL
                flagC = (v & 0x80) != 0;
                r = (v << 1) & 0xFF;
                setNZ8(r);
                flagV = flagN ^ flagC;
                return r;

            case 0x09: // ROL
                r = ((v << 1) | (flagC ? 0x01 : 0x00)) & 0xFF;
                flagC = (v & 0x80) != 0;
                setNZ8(r);
                flagV = flagN ^ flagC;
                return r;

            case 0x0A: // DEC
                r = (v - 1) & 0xFF;
                flagV = (v == 0x80);       // 0x80 - 1 overflows
                setNZ8(r);
                return r;

            case 0x0C: // INC
                r = (v + 1) & 0xFF;
                flagV = (v == 0x7F);       // 0x7F + 1 overflows
                setNZ8(r);
                return r;

            case 0x0D: // TST)
                setNZ8(v);
                flagV = false;
                flagC = false;
                return v;

            default:   // 0x0F and CLR set it to zero
                flagN = false;
                flagZ = true;
                flagV = false;
                flagC = false;
                return 0;
        }
    }

    //opcodes 0x80-0xFF
    private void executeTwoOperand(int op) {
        int high = op >> 4;
        int low = op & 0x0F;

        // finds the mode
        int mode;
        switch (high & 0x03) {
            case 0:  mode = IMM; break;
            case 1:  mode = DIR; break;
            case 2:  mode = IDX; break;
            default: mode = EXT; break;
        }
        boolean useB = (high >= 0xC);

        //16 bit subroutine instructions
        if (low >= 0x0C) {
            if (!useB) {
                switch (low) {
                    case 0x0C: cpx(mode); return;                            // CPX
                    case 0x0D:                                               // BSR / JSR
                        if (mode == IMM) {
                            bsr();
                        } else if (mode == DIR) {
                            illegal(op);
                        } else {
                            jsr(mode);
                        }
                        return;
                    case 0x0E: lds(mode); return;                            // LDS
                    default:                                                 // STS
                        if (mode == IMM) {
                            illegal(op);
                        } else {
                            sts(mode);
                        }
                        return;
                }
            } else {
                switch (low) {
                    case 0x0E: ldx(mode); return;                            // LDX
                    case 0x0F:                                               // STX
                        if (mode == IMM) {
                            illegal(op);
                        } else {
                            stx(mode);
                        }
                        return;
                    default:
                        illegal(op);
                        return;
                }
            }
        }

        //8 bit instructions
        if (low == 0x03 || (low == 0x07 && mode == IMM)) {
            illegal(op);
            return;
        }

        int addr = operandAddress(mode);
        int acc = useB ? b : a;
        int result = acc;
        boolean store = true;          // do we need to store it

        switch (low) {
            case 0x00: // SUB
                result = sub8(acc, memory.read(addr), 0);
                break;
            case 0x01: // CMP
                sub8(acc, memory.read(addr), 0);
                store = false;
                break;
            case 0x02: // SBC
                result = sub8(acc, memory.read(addr), flagC ? 1 : 0);
                break;
            case 0x04: // AND
                result = acc & memory.read(addr);
                setNZ8(result);
                flagV = false;
                break;
            case 0x05: // BIT
                setNZ8(acc & memory.read(addr));
                flagV = false;
                store = false;
                break;
            case 0x06: // LDA
                result = memory.read(addr);
                setNZ8(result);
                flagV = false;
                break;
            case 0x07: // STA
                memory.write(addr, acc);
                setNZ8(acc);
                flagV = false;
                store = false;
                break;
            case 0x08: // EOR
                result = acc ^ memory.read(addr);
                setNZ8(result);
                flagV = false;
                break;
            case 0x09: // ADC
                result = add8(acc, memory.read(addr), flagC ? 1 : 0);
                break;
            case 0x0A: // ORA
                result = acc | memory.read(addr);
                setNZ8(result);
                flagV = false;
                break;
            default:
                result = add8(acc, memory.read(addr), 0);
                break;
        }

        if (store) {
            if (useB) {
                b = result;
            } else {
                a = result;
            }
        }
    }

    //other 16 bit instructions
    private void cpx(int mode) {
        int addr = operandAddress16(mode);
        int m = memory.read16(addr);
        int result = (x - m) & 0xFFFF;
        setNZ16(result);
        flagV = ((x ^ m) & (x ^ result) & 0x8000) != 0;
    }

    //LDS
    private void lds(int mode) {
        int addr = operandAddress16(mode);
        sp = memory.read16(addr);
        setNZ16(sp);
        flagV = false;
    }

    //LDX
    private void ldx(int mode) {
        int addr = operandAddress16(mode);
        x = memory.read16(addr);
        setNZ16(x);
        flagV = false;
    }

    //STS
    private void sts(int mode) {
        int addr = operandAddress16(mode);
        memory.write(addr, sp >> 8);
        memory.write(addr + 1, sp & 0xFF);
        setNZ16(sp);
        flagV = false;
    }

    //STX
    private void stx(int mode) {
        int addr = operandAddress16(mode);
        memory.write(addr, x >> 8);
        memory.write(addr + 1, x & 0xFF);
        setNZ16(x);
        flagV = false;
    }

    //subroutine calls
    private void bsr() {
        int offset = (byte) fetch8();
        push16(pc);
        pc = (pc + offset) & 0xFFFF;
    }

    //JSR
    private void jsr(int mode) {
        int target = operandAddress(mode);
        push16(pc);
        pc = target;
    }


    //builds 8 bit ccr
    public int getCCR() {
        int ccr = 0xC0;
        if (flagH) ccr |= 0x20;
        if (flagI) ccr |= 0x10;
        if (flagN) ccr |= 0x08;
        if (flagZ) ccr |= 0x04;
        if (flagV) ccr |= 0x02;
        if (flagC) ccr |= 0x01;
        return ccr;
    }

    //sets the flags from an 8 bit value
    public void setCCR(int value) {
        flagH = (value & 0x20) != 0;
        flagI = (value & 0x10) != 0;
        flagN = (value & 0x08) != 0;
        flagZ = (value & 0x04) != 0;
        flagV = (value & 0x02) != 0;
        flagC = (value & 0x01) != 0;
    }

    //getters and setters
    public int getA()  { return a; }
    public int getB()  { return b; }
    public int getX()  { return x; }
    public int getSP() { return sp; }
    public int getPC() { return pc; }

    public void setA(int value)  { a = value & 0xFF; }
    public void setB(int value)  { b = value & 0xFF; }
    public void setX(int value)  { x = value & 0xFFFF; }
    public void setSP(int value) { sp = value & 0xFFFF; }
    public void setPC(int value) { pc = value & 0xFFFF; }

    public boolean getH() { return flagH; }
    public boolean getI() { return flagI; }
    public boolean getN() { return flagN; }
    public boolean getZ() { return flagZ; }
    public boolean getV() { return flagV; }
    public boolean getC() { return flagC; }

    //true if waiting for interrupt
    public boolean isWaiting() { return waiting; }

    //true if stopped
    public boolean isHalted() { return halted; }

    //saying why it stopped
    public String getHaltReason() { return haltReason; }

    //how much has run since reset
    public long getInstructionCount() { return instructionCount; }


    //true if step does something (not halted and not in wai)
    public boolean canRun() {
        if (halted) {
            return false;
        }
        if (!waiting) {
            return true;
        }
        return nmiPending || (irqPending && !flagI);
    }

   //clears halt/wai state
    public void clearStopState() {
        waiting = false;
        halted = false;
        haltReason = "";
    }
}
