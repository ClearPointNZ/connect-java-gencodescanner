package cd.connect.scanner.filters;

import javax.servlet.DispatcherType;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@WebFilter(description = "sample", displayName = "sample", filterName = "simplefilter", urlPatterns = "/*",
	initParams = {@WebInitParam(name = "sausage", value = "cumberlands"), @WebInitParam(name="sausage2", value = "kielbasa", description = "yummy")},
  dispatcherTypes = {DispatcherType.ASYNC}, asyncSupported = true)
public class SampleFilter {
}
