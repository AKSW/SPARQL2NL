/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import org.aksw.assessment.question.answer.Answer;
import com.hp.hpl.jena.query.Query;
import java.util.List;

/**
 *
 * @author ngonga
 */
public class SimpleQuestion implements Question {
    String text;
    List<Answer> correctAnswers;
    List<Answer> wrongAnswers;    
    int difficulty;
    Query query;
    QuestionType type;
    public SimpleQuestion(String text, List<Answer> correctAnswers, List<Answer> wrongAnswers, int difficulty, Query q, QuestionType type)
    {
        this.text = text;
        this.correctAnswers = correctAnswers;
        this.wrongAnswers = wrongAnswers;
        this.difficulty = difficulty;
        this.query = q;
        this.type = type;
    }
    
    public String getText() {
        return text;
    }
    
    public QuestionType getType()
    {
        return type;
    }

    @Override
    public String toString()
    {
        return text;
    }
    
    public List<Answer> getCorrectAnswers() {
        return correctAnswers;
    }
    
    public List<Answer> getWrongAnswers() {
        return wrongAnswers;
    }
    
    public int getDifficulty() {
        return difficulty;
    }

    public Query getQuery() {
        return query;
    }
    
}
