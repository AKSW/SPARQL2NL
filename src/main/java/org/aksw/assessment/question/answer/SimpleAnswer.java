/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question.answer;

import org.aksw.assessment.question.answer.Answer;

/**
 *
 * @author ngonga
 */
public class SimpleAnswer implements Answer{
    
	String text;
    String hint;
    
    public SimpleAnswer(String answer){
        this(answer, null);
    }
    
    public SimpleAnswer(String answer, String hint){
        text = answer;
        this.hint = hint;
    }

    public String getText() {
     return text;
    }
    
    /* (non-Javadoc)
	 * @see org.aksw.assessment.question.answer.Answer#getHint()
	 */
	@Override
	public String getHint() {
		return null;
	}
	
	/**
	 * @param hint the hint to set
	 */
	public void setHint(String hint) {
		this.hint = hint;
	}
    
    @Override
    public String toString()
    {
        return text;
    }

	
}
