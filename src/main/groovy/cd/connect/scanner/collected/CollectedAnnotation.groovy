package cd.connect.scanner.collected

import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.MemberValuePair
import com.github.javaparser.ast.expr.NormalAnnotationExpr

/**
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
class CollectedAnnotation extends HashMap<String, String> {
	private final AnnotationExpr annotation

	CollectedAnnotation(AnnotationExpr annotation) {
		this.annotation = annotation

		if (annotation instanceof NormalAnnotationExpr) {
			NormalAnnotationExpr na = NormalAnnotationExpr.class.cast(annotation)
			na.pairs.each { MemberValuePair mvp ->
				String value = mvp.value.toString()?.trim()
				put(mvp.name.asString(), value)
			}
		}
	}

	AnnotationExpr getAnnotation() {
		return annotation
	}
}
