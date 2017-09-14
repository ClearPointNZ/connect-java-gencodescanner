package cd.connect.scanner.model

import org.apache.maven.plugins.annotations.Parameter

/**
 * Created by Richard Vowles on 3/07/17.
 */
class BaseTemplate {
	@Parameter
	String className

	protected String simpleName
	protected String packageName

	String getSimpleName() {
		return simpleName
	}

	String getPackageName() {
		return packageName
	}

	public void setClassName(String cName) {
		this.className = cName

		int packageSep = cName.lastIndexOf('.')
		if (packageSep != -1) {
			this.simpleName = cName.substring(packageSep + 1)
			this.packageName = cName.substring(0, packageSep)
		} else {
			this.simpleName = className
			this.packageName = ""
		}
	}
}
