package cd.connect.scanner.filters;

import javax.servlet.annotation.WebFilter;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@WebFilter(filterName = "peanuts", urlPatterns = {"/nuts/*", "groundnuts/*"})
@Deprecated
public class Sample2Filter {
}
