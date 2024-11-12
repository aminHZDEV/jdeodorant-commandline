package main.java.jdeodorant.refactoring.views;

import org.eclipse.jface.text.source.Annotation;

public class SliceAnnotation extends Annotation {
	public static final String EXTRACTION = "main.main.jdeodorant.extractionAnnotation";
	public static final String DUPLICATION = "main.main.jdeodorant.duplicationAnnotation";

	public SliceAnnotation(String type, String text) {
		super(type, false, text);
	}
}
