package cd.connect.scanner.components;

import javax.inject.Inject;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class Component1 {
	@Inject
	private Component2 component2;

	@Inject
	public void setComponent3(Component3 c3) {}

	@Inject
	public void setInteger(Integer i) {}
}
