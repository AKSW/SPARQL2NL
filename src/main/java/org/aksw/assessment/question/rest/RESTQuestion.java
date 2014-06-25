/**
 * 
 */
package org.aksw.assessment.question.rest;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Lorenz Buehmann
 *
 */
@XmlRootElement
public class RESTQuestion {

	private String question;
	private String questionType;
	private List<RESTAnswer> correctAnswers;
	private List<RESTAnswer> wrongAnswers;
	
	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public List<RESTAnswer> getCorrectAnswers() {
		return correctAnswers;
	}

	public void setCorrectAnswers(List<RESTAnswer> correctAnswers) {
		this.correctAnswers = correctAnswers;
	}

	public List<RESTAnswer> getWrongAnswers() {
		return wrongAnswers;
	}

	public void setWrongAnswers(List<RESTAnswer> wrongAnswers) {
		this.wrongAnswers = wrongAnswers;
	}

	public String getQuestionType() {
		return questionType;
	}

	public void setQuestionType(String questionType) {
		this.questionType = questionType;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Question: " + question + "\nCorrect answers: " + correctAnswers + "\nWrong answers: " + wrongAnswers;
	}
}
