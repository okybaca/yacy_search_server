// ymage8BitFontGenerator.java
// ---------------------------
// by Marc Nause
// created: 05.04.2007
//
// based on:
// ymageFontGenerator.java
// ---------------------------
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2005
// created: 31.10.2005
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.visualization;

public class FontGenerator8Pixel { 

//A valid ymage font has 96 characters:

/*
0x20: !"#$%&'
0x28:()*+,-./
0x30:01234567
0x38:89:;<=>?
0x40:@ABCDEFG
0x48:HIJKLMNO
0x50:PQRSTUVW
0x58:XYZ[\]^_
0x60:`abcdefg
0x68:hijklmno
0x70:pqrstuvw
0x78:xyz{|}~
*/

//Each character has the same height of m lines with n pixels each.
//While m does not need to equal n both have to stay consistend through
//the whole font.

    public static final String[][] font =
        {
            {"........", //0x20
             "........",
             "........",
             "........",
             "........",
             "........",
             "........",
             "........"},
            {"...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "........",
             "...X....",
             "........"},
            {"..X.X...",
             "..X.X...",
             "........",
             "........",
             "........",
             "........",
             "........",
             "........"},
            {"..X.X...",
             "..X.X...",
             "XXXXXXX.",
             "..X.X...",
             "XXXXXXX.",
             "..X.X...",
             "..X.X...",
             "........"},
            {"...XX...",
             ".XXXXXX.",
             "X..XX...",
             ".XXXXX..",
             "...XX.X.",
             "XXXXXX..",
             "...X....",
             "........"},
            {".XX...X.",
             ".XX..X..",
             "....X...",
             "...X....",
             "..X.....",
             ".X...XX.",
             "X....XX.",
             "........"},
            {".XX.....",
             "X..X....",
             ".XX.....",
             "..X...X.",
             "X..X.X..",
             "X...X...",
             ".XXX..X.",
             "........"},
            {"...X....",
             "...X....",
             "........",
             "........",
             "........",
             "........",
             "........",
             "........"},
            {"....X...",
             "...X....",
             "..X.....",
             "..X.....",
             "..X.....",
             "...X....",
             "....X...",
             "........"},
            {"...X....",
             "....X...",
             ".....X..",
             ".....X..",
             ".....X..",
             "....X...",
             "...X....",
             "........"},
            {"...X....",
             ".X.X.X..",
             "..XXX...",
             "XXXXXXX.",
             "..XXX...",
             ".X..X.X.",
             "...X....",
             "........"},
            {"........",
             "...X....",
             "...X....",
             ".XXXXX..",
             "...X....",
             "...X....",
             "........",
             "........"},
            {"........",
             "........",
             "........",
             "........",
             "........",
             "...XX...",
             "...XX...",
             "....X..."},
            {"........",
             "........",
             "........",
             ".XXXXX..",
             "........",
             "........",
             "........",
             "........"},
            {"........",
             "........",
             "........",
             "........",
             "........",
             "...XX...",
             "...XX...",
             "........"},
            {"......X.",
             ".....X..",
             "....X...",
             "...X....",
             "..X.....",
             ".X......",
             "X.......",
             "........"},
            {".XXXXX..",
             "X....XX.",
             "X...X.X.",
             "X..X..X.",
             "X.X...X.",
             "XX....X.",
             ".XXXXX..",
             "........"},
            {"...X....",
             "..XX....",
             ".X.X....",
             "...X....",
             "...X....",
             "...X....",
             ".XXXXX..",
             "........"},
            {"..XXX...",
             ".X...X..",
             ".....X..",
             "....X...",
             "...X....",
             "..X.....",
             ".XXXXX..",
             "........"},
            {"..XXX...",
             ".X...X..",
             ".....X..",
             "...XX...",
             ".....X..",
             ".X...X..",
             "..XXX...",
             "........"},
            {".....X..",
             "....XX..",
             "...X.X..",
             "..X..X..",
             ".XXXXXX.",
             ".....X..",
             ".....X..",
             "........"},
            {".XXXXXX.",
             ".X......",
             ".X......",
             ".XXXXX..",
             "......X.",
             "......X.",
             ".XXXXX..",
             "........"},
            {"..XXX...",
             ".X...X..",
             ".X......",
             ".XXXX...",
             ".X...X..",
             ".X...X..",
             "..XXX...",
             "........"},
            {".XXXXX..",
             ".....X..",
             ".....X..",
             "....X...",
             "...X....",
             "...X....",
             "...X....",
             "........"},
            {"..XXX...",
             ".X...X..",
             ".X...X..",
             "..XXX...",
             ".X...X..",
             ".X...X..",
             "..XXX...",
             "........"},
            {"..XXX...",
             ".X...X..",
             ".X...X..",
             "..XXXX..",
             ".....X..",
             ".X...X..",
             "..XXX...",
             "........"},
            {"........",
             "........",
             "...XX...",
             "...XX...",
             "........",
             "...XX...",
             "...XX...",
             "........"},
            {"........",
             "........",
             "...XX...",
             "...XX...",
             "........",
             "...XX...",
             "...XX...",
             "....X..."},
            {".....XX.",
             "...XX...",
             ".XX.....",
             "X.......",
             ".XX.....",
             "...XX...",
             ".....XX.",
             "........"},
            {"........",
             "........",
             "..XXXX..",
             "........",
             "..XXXX..",
             "........",
             "........",
             "........"},
            {"XX......",
             "..XX....",
             "....XX..",
             "......X.",
             "....XX..",
             "..XX....",
             "XX......",
            "........",},
            {"..XXX...",
             ".X...X..",
             ".....X..",
             "....X...",
             "...X....",
             "........",
             "...X....",
             "........"},
            {".XXXXX..",
             "X.....X.",
             "X...XXX.",
             "X..X..X.",
             "X...XXX.",
             "X.......",
             ".XXXXXX.",
             "........"},
            {".XXXXX..",
             "X.....X.",
             "X.....X.",
             "XXXXXXX.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "........"},
            {"XXXXXX..",
             "X.....X.",
             "X.....X.",
             "XXXXXX..",
             "X.....X.",
             "X.....X.",
             "XXXXXX..",
             "........"},
            {"..XXXX..",
             ".X....X.",
             "X.......",
             "X.......",
             "X.......",
             ".X....X.",
             "..XXXX..",
             "........"},
            {"XXXXX...",
             "X....X..",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X....X..",
             "XXXXX...",
             "........"},
            {"XXXXXXX.",
             "X.......",
             "X.......",
             "XXXXXX..",
             "X.......",
             "X.......",
             "XXXXXXX.",
             "........"},
            {"XXXXXXX.",
             "X.......",
             "X.......",
             "XXXXXX..",
             "X.......",
             "X.......",
             "X.......",
             "........"},
            {".XXXXX..",
             "X.......",
             "X.......",
             "X..XXXX.",
             "X.....X.",
             "X.....X.",
             ".XXXXX..",
             "........"},
            {"X.....X.",
             "X.....X.",
             "X.....X.",
             "XXXXXXX.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "........"},
            {"..XXX...",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "..XXX...",
             "........"},
            {"...XXX..",
             "....X...",
             "....X...",
             "....X...",
             "....X...",
             "X...X...",
             ".XXX....",
             "........"},
            {"X....XX.",
             "X...XX..",
             "X..XX...",
             "XXX.....",
             "X..XX...",
             "X...XX..",
             "X....XX.",
             "........"},
            {"X.......",
             "X.......",
             "X.......",
             "X.......",
             "X.......",
             "X.......",
             "XXXXXXX.",
             "........"},
            {"X.....X.",
             "XX...XX.",
             "X.X.X.X.",
             "X..X..X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "........"},
            {"X.....X.",
             "XX....X.",
             "X.X...X.",
             "X..X..X.",
             "X...X.X.",
             "X....XX.",
             "X.....X.",
             "........"},
            {".XXXXX..",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             ".XXXXX..",
             "........"},
            {"XXXXXX..",
             "X.....X.",
             "X.....X.",
             "XXXXXX..",
             "X.......",
             "X.......",
             "X.......",
             "........"},
            {".XXXXX..",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X...X.X.",
             ".XXXXX..",
             "......X."},
            {"XXXXXX..",
             "X.....X.",
             "X.....X.",
             "XXXXXX..",
             "X...X...",
             "X....X..",
             "X.....X.",
             "........"},
            {".XXXXX..",
             "X.....X.",
             "X.......",
             ".XXXXX..",
             "......X.",
             "X.....X.",
             ".XXXXX..",
             "........"},
            {"XXXXXXX.",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "........"},
            {"X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             ".XXXXX..",
             "........"},
            {"X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             ".X...X..",
             "..X.X...",
             "...X....",
             "........"},
            {"X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X..X..X.",
             "X.X.X.X.",
             ".X...X..",
             "........"},
            {"X.....X.",
             ".X...X..",
             "..X.X...",
             "...X....",
             "..X.X...",
             ".X...X..",
             "X.....X.",
             "........"},
            {"X.....X.",
             ".X...X..",
             "..X.X...",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "........"},
            {"XXXXXXX.",
             ".....X..",
             "....X...",
             "...X....",
             "..X.....",
             ".X......",
             "XXXXXXX.",
             "........"},
            {"..XXXX..",
             "..X.....",
             "..X.....",
             "..X.....",
             "..X.....",
             "..X.....",
             "..XXXX..",
             "........"},
            {"X.......",
             ".X......",
             "..X.....",
             "...X....",
             "....X...",
             ".....X..",
             "......X.",
             "........"},
            {"..XXXX..",
             ".....X..",
             ".....X..",
             ".....X..",
             ".....X..",
             ".....X..",
             "..XXXX..",
             "........"},
            {"...X....",
             "..X.X...",
             ".X...X..",
             "........",
             "........",
             "........",
             "........",
             "........"},
            {"........",
             "........",
             "........",
             "........",
             "........",
             "........",
             "XXXXXXX.",
             "........"},
            {"...X....",
             "....X...",
             "........",
             "........",
             "........",
             "........",
             "........",
             "........"},
            {"........",
             "........",
             ".XXXX...",
             ".....X..",
             ".XXXXX..",
             "X....X..",
             ".XXXX.X.",
             "........"},
            {"X.......",
             "X.......",
             "X.......",
             "XXXXXX..",
             "X.....X.",
             "X.....X.",
             "XXXXXX..",
             "........"},
            {"........",
             "........",
             ".XXXXXX.",
             "X.......",
             "X.......",
             "X.......",
             ".XXXXXX.",
             "........"},
            {"......X.",
             "......X.",
             "......X.",
             ".XXXXXX.",
             "X.....X.",
             "X.....X.",
             ".XXXXXX.",
             "........"},
            {"........",
             "........",
             ".XXXXX..",
             "X.....X.",
             "XXXXXXX.",
             "X.......",
             ".XXXXX..",
             "........"},
            {"...XX...",
             "..X.....",
             "..X.....",
             ".XXX....",
             "..X.....",
             "..X.....",
             "..X.....",
             "........"},
            {"........",
             "........",
             ".XXXXX..",
             "X.....X.",
             "X.....X.",
             ".XXXXXX.",
             "......X.",
             ".XXXXX.."},
            {"X.......",
             "X.......",
             "X.......",
             "XXXXXX..",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "........"},
            {"........",
             "...X....",
             "........",
             "..XX....",
             "...X....",
             "...X....",
             "..XXX...",
             "........"},
            {"........",
             "....X...",
             "........",
             "...XX...",
             "....X...",
             "....X...",
             "....X...",
             "..XX...."},
            {"X.......",
             "X.......",
             "X....XX.",
             "X..XX...",
             "XXX.....",
             "X..XX...",
             "X....XX.",
             "........"},
            {"..XX....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "..XXX...",
             "........"},
            {"........",
             "........",
             ".XX.XX..",
             "X..X..X.",
             "X..X..X.",
             "X..X..X.",
             "X..X..X.",
             "........"},
            {"........",
             "........",
             ".XXXXX..",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "........"},
            {"........",
             "........",
             ".XXXXX..",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             ".XXXXX..",
             "........"},
            {"........",
             "........",
             "XXXXXX..",
             "X.....X.",
             "X.....X.",
             "XXXXXX..",
             "X.......",
             "X......."},
            {"........",
             "........",
             ".XXXXXX.",
             "X.....X.",
             "X.....X.",
             ".XXXXXX.",
             "......X.",
             "......X."},
            {"........",
             "........",
             "X.XXXX..",
             "XX....X.",
             "X.......",
             "X.......",
             "X.......",
             "........"},
            {"........",
             "........",
             ".XXXXX..",
             "X.......",
             ".XXXXX..",
             "......X.",
             ".XXXXX..",
             "........"},
            {"...X....",
             "...X....",
             "...X....",
             ".XXXXX..",
             "...X....",
             "...X....",
             "....XX..",
             "........"},
            {"........",
             "........",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             ".XXXXX..",
             "........"},
            {"........",
             "........",
             "X.....X.",
             "X.....X.",
             ".X...X..",
             "..X.X...",
             "...X....",
             "........"},
            {"........",
             "........",
             "X.....X.",
             "X.....X.",
             "X..X..X.",
             "X.X.X.X.",
             ".X...X..",
             "........"},
            {"........",
             "........",
             "X.....X.",
             ".XX.XX..",
             "...X....",
             ".XX.XX..",
             "X.....X.",
             "........"},
            {"........",
             "........",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             ".XXXXXX.",
             "......X.",
             ".XXXXX.."},
            {"........",
             "........",
             "XXXXXXX.",
             "....XX..",
             "...X....",
             ".XX.....",
             "XXXXXXX.",
             "........"},
            {"...XX...",
             "..X.....",
             "..X.....",
             "XX......",
             "..X.....",
             "..X.....",
             "...XX...",
             "........"},
            {"...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X....",
             "...X...."},
            {"...XX...",
             ".....X..",
             ".....X..",
             "......XX",
             ".....X..",
             ".....X..",
             "...XX...",
             "........"},
            {"........",
             "........",
             "..X.....",
             ".X.XX.X.",
             ".....X..",
             "........",
             "........",
             "........"},
            {"XXXXXXX.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "X.....X.",
             "XXXXXXX.",
             "........"},


        };


    public static void main(final String[] args) {
        String[] letter;
        int b;
        int v;
        int c = 0;
        int n = -1;
        int m = -1;
        String s;

        //check if font has correct number of letters
        if(font.length != 96){
            System.out.println("\nWARNING: Font has "+font.length+" letters, should have 96 letters.");
            System.out.println("         Font will be invalid!\n");
        }

        for (int i = 0; i < font.length; i++) {
            letter = font[i];
            b = 0;

            //check if all letters have same numbers of lines
            if((letter.length != n) && (n == -1)) {
                n = letter.length;
            }
            else if(letter.length != n) {
                System.out.println("\n\nWARNING: Letter number "+i+" has different number of lines ("+letter.length+") than predecessors ("+n+").");
                System.out.println("         Font will be invalid!\n");
            }


            for (int j = 0; j < letter.length; j++) {

                b = b << letter[j].length();
                v = 16;

                //check if all letters have same number of lines
                if((letter[j].length() != m) && (m == -1)) {
                    m=letter[j].length();
                }
                else if(letter[j].length() != m) {
                    System.out.println("\n\nWARNING: Letter number "+i+" has a different number of bits ("+letter[j].length()+") than predecessors ("+m+") in line "+j+".");
                    System.out.println("         Font will be invalid!\n");
                }

                for (int col = 0; col < letter[j].length(); col++) {

                    if (letter[j].charAt(col) == 'X')  b += v;
                    v = v >> 1;
                }
            }
            s = Long.toHexString(b).toUpperCase();
            while (s.length() < (letter.length*m/8)) s = "0" + s;
            System.out.print("0x" + s + ",");
            c++;
            if (c >= 8) {
                System.out.println();
                c = 0;
            }
        }
    } 

} 