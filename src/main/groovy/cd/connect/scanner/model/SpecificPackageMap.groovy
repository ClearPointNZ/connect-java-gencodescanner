package cd.connect.scanner.model

import groovy.transform.ToString

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@ToString
class SpecificPackageMap {
	String packageName
	private int len
	Set<String> groups = new HashSet<>()
	Set<String> requiredAnnotations = new HashSet<>()

	void setPackageName(String p) {
		this.packageName = p
		this.len = p.length()
	}

	int getLen() {
		return len
	}
}
