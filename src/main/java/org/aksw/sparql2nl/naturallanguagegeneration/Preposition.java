/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.naturallanguagegeneration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author ngonga
 */
public class Preposition {

    Set set;

    public Preposition(InputStream is) {
        set = new HashSet();
        try {
            BufferedReader bufRdr = new BufferedReader(new InputStreamReader(is));
            String s = bufRdr.readLine();
            while (s != null) {
                s = s.toLowerCase();
                set.add(s.trim());
                s = bufRdr.readLine();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public boolean isPreposition(String s)
    {
        return set.contains(s);
    }
}
