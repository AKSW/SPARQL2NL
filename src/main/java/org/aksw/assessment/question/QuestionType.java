/**
 * 
 */
package org.aksw.assessment.question;

/**
 * @author Lorenz Buehmann
 *
 */
public enum QuestionType {
	
	MC("mc"), JEOPARDY("jeopardy"), TRUEFALSE("truefalse");
	
	String name;
	
	private QuestionType(String name) {
		this.name = name;
	}
	
	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

}
