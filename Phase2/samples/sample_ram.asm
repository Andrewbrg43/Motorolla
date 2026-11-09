* sample_ram.asm - adds 10+9+...+1 and stores the answer (55 = $37) at $0020.
        ORG     $0000
        LDAA    #0              total = 0
        LDAB    #10             counter = 10
LOOP    ABA                     total = total + counter
        DECB                    counter = counter - 1
        BNE     LOOP            repeat until counter is 0
        STAA    $20             save the answer
        WAI                     stop so the registers can be inspected
