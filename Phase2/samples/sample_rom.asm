* sample_rom.asm - trivial "monitor" ROM with the interrupt vector table.
* RESET jumps to $0000 (where the RAM program starts).
* IRQ, SWI and NMI all point at a single RTI so they return harmlessly.
        ORG     $F000
START   JMP     $0000
HANDLER RTI

* Vector table: each vector is 2 bytes, high byte first.
        ORG     $FFF8
        FDB     HANDLER         IRQ   ($FFF8/$FFF9)
        FDB     HANDLER         SWI   ($FFFA/$FFFB)
        FDB     HANDLER         NMI   ($FFFC/$FFFD)
        FDB     START           RESET ($FFFE/$FFFF)
