package ast.visualization;

import jdeodorant.refactoring.views.FeatureEnviedMethodInformationControl;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.swt.widgets.Shell;

public class FeatureEnviedMethodInformationControlCreator implements
		ICustomInformationControlCreator {

	public IInformationControl createInformationControl(Shell parent) {
		//return new InformationControl(parent, true);
		ToolBarManager manager = new ToolBarManager();
		return new FeatureEnviedMethodInformationControl(parent, manager);
	}

	public boolean isSupported(Object info) {
		return info instanceof PMClassFigure;
	}

}
