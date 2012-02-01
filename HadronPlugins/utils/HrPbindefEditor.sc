HrPbindefEditor : SCViewHolder {
	var <model;

	// 'view' is the wrapper, CompositeView
	// this is the main thing inside, which may contain other things
	var mainView;
	var subpats;

	*new { |parent, bounds, key|
		^super.new.init(parent, bounds, key)
	}

	init { |parent, bounds, key|
		this.view = CompositeView(parent, bounds);
		
	}
}

HrPatternLine : SCViewHolder {
	var <key, <text, <model, <index, <isLast = false;
	var label, editor;
	var reorderSink, reorderDrag, plusBtn, minusBtn;
	var setPattern;  // used to know if we should respond to proxy notification
	var <background, <errorState = false;

	*new { |parent, bounds, key, model, text, index|
		^super.new.init(parent, bounds, key, model, text, index)
	}

	init { |parent, bounds, argKey, argModel, argText, argIndex|
		var buttonSize = Library.at(HrPatternLine, \buttonSize) ? 20,
		buttonPoint = buttonSize @ buttonSize,
		height = bounds.height - 4;

		// note, the reorderSink will be drawn outside this
		this.view = FlowView(parent,
			bounds.copy.left_(bounds.left + buttonSize + 2)
			.width_(bounds.width - buttonSize - 2),
			Point(2, 2), Point(2, 2)
		);
		background = view.background;

		index = argIndex ? 0;
		reorderDrag = DragSource(view, buttonPoint)
		.object_("p" ++ index).align_(\center);

		plusBtn = Button(view, buttonPoint)
		.states_([["+"]])
		.action_({ this.changed(\addRow, index) });

		minusBtn = Button(view, buttonPoint)
		.states_([["-"]])
		.action_({ this.changed(\deleteRow, index) });

		key = argKey;
		label = TextField(view, 100@height).string_(key).align_(\center)
		.action_({ |view|
			key = view.string.asSymbol;
			this.changed(\key, key);
		});

		model = argModel;
		text = argText;
		if(model.isNil and: { text.notNil }) {
			try {
				model = text.interpret
			} { |err|
				err.reportError;
				"^^ Invalid string given to pattern editor line".postln;
			};
		};
		// don't need HrEventPatternProxy: this is just for value patterns
		if(model.isKindOf(Pattern) and: { model.isKindOf(HrPatternProxy).not }) {
			model = HrPatternProxy(model.tryPerform(\source) ? model);
		};
		if(text.isNil) { text = (model.tryPerform(\source) ? model).asCompileString };
		model.addDependant(this);

		editor = TextField(view, view.indentedRemaining.height_(height)).string_(text)
		.action_({ |eview|
			try {
				setPattern = eview.string.interpret;
				if(setPattern.isNil) {
					Error("Parse error in pattern editor line").throw;
				};
				text = eview.string;
				model.source = setPattern;
				view.background = background;
				errorState = false;
			} { |err|
				err.reportError;
				"^^ Invalid string given to pattern editor line".postln;
				view.background = Color(1, 0.8, 0.8);
				errorState = true;
			};
		});

		// here's the outlier
		reorderSink = DragSink(parent, Rect(
			bounds.left,
			bounds.top + (bounds.height * 0.5),
			buttonSize, buttonSize
		))
		.string_("O").align_(\center)
		.canReceiveDragHandler_({
			var str = View.currentDrag;
			str.isString and: {
				str[0] == $p and: { str[1..].every(_.isDecDigit) }
			}
		})
		.receiveDragHandler_({ |view|
			var i = View.currentDrag[1..].asInteger;
			view.string = "O";
			if(i != index and: { i != index - 1 }) {
				this.changed(\reorder, index, i);
			};
		});
	}

	index_ { |i|
		index = i;
		reorderDrag.object_("p" ++ i);
	}

	isLast_ { |bool(false)|
		isLast = bool;
		defer { reorderSink.visible = isLast.not };
	}

	background_ { |color|
		if(color.isKindOf(Color)) {
			background = color;
			if(errorState.not) {
				view.background = background;
			}
		}
	}

	update { |obj, what ... more|
		switch(what)
		{ \source } {
			if(obj.source !== setPattern) {
				setPattern = obj.source;
				defer { editor.string = obj.source.asCompileString };
			};
			this.changed(\source, setPattern)
		}
	}

	model_ { |obj|
		model.removeDependant(this);
		model = obj;
		model.addDependant(this);
	}

	viewDidClose {
		if(view.parent.isClosed.not) {
			reorderSink.remove;
		};
		model.removeDependant(this);
		view = nil;
		this.changed(\viewDidClose);
	}
}