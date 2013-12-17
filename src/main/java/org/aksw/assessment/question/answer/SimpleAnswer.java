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
    public SimpleAnswer(String answer)
    {
        text = answer;
    }

    public String getText() {
     return text;
    }
    
    @Override
    public String toString()
    {
        return text;
    }
}
