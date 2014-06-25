/**
 * 
 */
package org.aksw.assessment.question.rest;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.Logger;

/**
 * @author Lorenz Buehmann
 *
 */
public class AssessServletContextListener implements ServletContextListener{
	
	private static final Logger logger = Logger.getLogger(AssessServletContextListener.class.getName());

	/* (non-Javadoc)
	 * @see javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
	 */
	@Override
	public void contextDestroyed(ServletContextEvent e) {
	}

	/* (non-Javadoc)
	 * @see javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
	 */
	@Override
	public void contextInitialized(ServletContextEvent e) {
		RESTService.init(e.getServletContext());
		new RESTService().precomputeGraphs(e.getServletContext());
	}

}
