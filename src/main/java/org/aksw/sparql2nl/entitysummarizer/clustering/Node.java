package org.aksw.sparql2nl.entitysummarizer.clustering;

import java.io.Serializable;

/**
 * 
 * @author ngonga
 */
public class Node implements Serializable {

	public String label;
	public boolean outgoing = true;

	public Node(String label) {
		this.label = label;
	}

	public Node(String label, boolean outgoing) {
		this.label = label;
		this.outgoing = outgoing;
	}

	public String toString() {
		return label;
	}
}
