package cd.connect.scanner.collected

import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.symbolsolver.model.declarations.ReferenceTypeDeclaration
import groovy.transform.CompileStatic

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@CompileStatic
class CollectedClass {
	@Delegate
	private ReferenceTypeDeclaration clazz
	private TypeDeclaration src
	Set<CollectedAnnotation> annotations = new HashSet<>()


	CollectedClass(ReferenceTypeDeclaration clazz, TypeDeclaration src) {
		this.clazz = clazz
		this.src = src
	}

	ReferenceTypeDeclaration getClazz() {
		return clazz
	}

	TypeDeclaration getSrc() {
		return src
	}

	public void setOriginalAnnotation(AnnotationExpr annotationExpr) {
		addOriginalAnnotation(annotationExpr)
	}

	public void addOriginalAnnotation(AnnotationExpr annotationExpr) {
		if (annotationExpr) {
			this.annotations.add(new CollectedAnnotation(annotationExpr))
		}
	}

	public void addAnnotation(CollectedAnnotation ca) {
		if (ca) {
			this.annotations.add(ca)
		}
	}

	public CollectedAnnotation getAnnotation() {
		if (annotations) {
			return annotations[0]
		} else {
			return null
		}
	}

	boolean equals(o) {
		if (this.is(o)) return true
		if (getClass() != o.class) return false

		CollectedClass that = (CollectedClass) o

		if (src != that.src) return false

		return true
	}

	int hashCode() {
		return (src != null ? src.hashCode() : 0)
	}
}
