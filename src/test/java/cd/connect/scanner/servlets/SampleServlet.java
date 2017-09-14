package cd.connect.scanner.servlets;

import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@WebServlet(name = "sample", urlPatterns = {"/sample", "/sample2"}, loadOnStartup = 10,
	initParams = {@WebInitParam(name = "sausage", value = "cumberlands"), @WebInitParam(name="sausage2", value = "kielbasa", description = "yummy")},
  description = "testing", asyncSupported = true)
public class SampleServlet {
}
