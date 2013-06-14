/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.util.Set;

import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.Property;

/**
 * This class basically represents a summarization of a class by containing a set of properties which
 * are most frequently used in specific a knowledge base.
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationTemplate {

	private NamedClass cls;
	private Set<Property> properties;
	
	public EntitySummarizationTemplate(NamedClass cls, Set<Property> properties) {
		this.cls = cls;
		this.properties = properties;
	}
	
	/**
	 * @return the class
	 */
	public NamedClass getTemplateClass() {
		return cls;
	}
	
	/**
	 * @return the properties
	 */
	public Set<Property> getProperties() {
		return properties;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Class: " + cls + "\nProperties: " + properties;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cls == null) ? 0 : cls.hashCode());
		result = prime * result + ((properties == null) ? 0 : properties.hashCode());
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
		EntitySummarizationTemplate other = (EntitySummarizationTemplate) obj;
		if (cls == null) {
			if (other.cls != null)
				return false;
		} else if (!cls.equals(other.cls))
			return false;
		if (properties == null) {
			if (other.properties != null)
				return false;
		} else if (!properties.equals(other.properties))
			return false;
		return true;
	}
	

}
