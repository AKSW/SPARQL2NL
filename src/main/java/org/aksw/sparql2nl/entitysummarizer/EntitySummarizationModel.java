/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.util.Set;

import com.google.common.base.Joiner;

/**
 * This class represents a entity summarization model for a given knowledge base, i.e. it contains
 * summarization templates for each class in the knowledge base, if exists.
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationModel {

	private Set<EntitySummarizationTemplate> templates;

	public EntitySummarizationModel(Set<EntitySummarizationTemplate> templates) {
		this.templates = templates;
	}
	
	/**
	 * @return the templates
	 */
	public Set<EntitySummarizationTemplate> getTemplates() {
		return templates;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return Joiner.on("\n").join(templates);
	}

}
