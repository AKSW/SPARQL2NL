/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.clustering;

/**
 *
 * @author ngonga
 */
public class Node {
    public String label;
    public Node(String label)
    {
        this.label = label;
    }
    
    public String toString()
    {
        return label;
    }
}