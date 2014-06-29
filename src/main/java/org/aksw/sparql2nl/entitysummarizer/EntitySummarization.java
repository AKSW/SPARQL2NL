/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.util.List;

import com.google.common.base.Joiner;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarization {
	
	private Resource entity;
	private List<Triple> triples; 
	
	public EntitySummarization(Resource entity, List<Triple> triples) {
		this.entity = entity;
		this.triples = triples;
	}
	
	/**
	 * @return the summarized entity
	 */
	public Resource getEntity() {
		return entity;
	}
	
	/**
	 * @return the triples of the summarization
	 */
	public List<Triple> getTriples() {
		return triples;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entity == null) ? 0 : entity.hashCode());
		result = prime * result + ((triples == null) ? 0 : triples.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		EntitySummarization other = (EntitySummarization) obj;
		if (entity == null) {
			if (other.entity != null)
				return false;
		} else if (!entity.equals(other.entity))
			return false;
		if (triples == null) {
			if (other.triples != null)
				return false;
		} else if (!triples.equals(other.triples))
			return false;
		return true;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Entity:" + entity + "Triples:\n" + Joiner.on("\n").join(triples);
	}

}
