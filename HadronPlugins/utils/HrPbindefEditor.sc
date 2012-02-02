// ok, i'm making a rule: this works on Pdef / Pbindef keys
// no passing in an arbitrary pattern

HrPbindefEditor : SCViewHolder {
	var <model, <key;

	// 'view' is the wrapper, CompositeView
	// this is the main thing inside, which may contain other things
	var mainView, <status;
	var subpats;
	var moveFirstView, addLastView;

	*new { |parent, bounds, key|
		^super.new.init(parent, bounds, key)
	}

	init { |parent, bounds, argKey|
		this.view = CompositeView(parent, bounds);
		this.key = argKey;
	}

	// 3 cases:
	//// non-pbind pattern: post code, instruct user to use code editor
	//// pbind pattern: turn into HrPbindef, present line views
	//// nil: message saying to choose a valid key
	makeView {
		var patTemp = this.convertPattern(model.source);
		var buttonSize = Library.at(HrPatternLine, \buttonSize) ? 20;
		case
		{ model.isNil } {
			if(status == \editing or: { mainView.isNil }) {
				this.clearView;
				mainView.remove;
				mainView = TextView(view, view.bounds)
				.resize_(5)
				.background_(Color.gray(0.9))
				.editable_(false);
			};
			mainView.string_("Set a valid pattern name to begin editing.");
			status = \empty;
		}
		{ patTemp.isNil } {
			if(status == \editing or: { mainView.isNil }) {
				this.clearView;
				mainView.remove;
				mainView = TextView(view, view.bounds)
				.background_(Color.gray(0.9))
				.resize_(5)
				.editable_(false);
			};
			mainView.string_("Only Pbind-style patterns can be edited by GUI.

Use an interactive code window to edit this pattern.

%".format(model.source.asCompileString));
			status = \idle;
		}
		{ patTemp.notNil } {
			if(status != \editing
				or: { mainView.isNil
					or: { subpats.size != (model.source.pairs.size div: 2) }
				}
			) {
				mainView.remove;
				this.clearView;  // which sets subpats to List.new - see below

				mainView = ScrollView(view, view.bounds)
				.resize_(5)
				.autohidesScrollers_(true)
				.hasVerticalScroller_(true)
				.hasHorizontalScroller_(false);

				moveFirstView = HrReorderSinkView(mainView, Rect(2, 2, buttonSize, buttonSize))
				.canReceiveDragHandler_({
					var str = View.currentDrag;
					str.isString and: {
						str[0] == $p and: { str[1..].every(_.isDecDigit) }
					}
				})
				.receiveDragHandler_({ |view|
					var i = View.currentDrag[1..].asInteger;
					if(i != 0) {
						this.update(nil, \reorder, -1, i);
					};
				});

				patTemp.pairs.pairsDo { |key, value, i|
					i = i >> 1;
					subpats.add(HrPatternLine(mainView,
						Rect(2, 14 + (24*i), mainView.bounds.width-4, 24),
						key, value, nil, i
					));
					subpats.last.addDependant(this);
				};

				addLastView = Button(mainView,
					Rect(48, 16 + (24*subpats.size), buttonSize, buttonSize))
				.states_([["+"]])
				.action_({ this.update(nil, \addRow, subpats.size) });
			};
			status = \editing;
		};
	}

	key_ { |obj|
		if(obj.isNil) {
			key = nil;
			model.removeDependant(this);
			model = nil;
			^this.makeView
		};
		try {
			model.removeDependant(this);
			// Pbindef(obj) will throw error if obj is not a symbol
			// if this is a HrPdef, it'll try to convert to HrPbindef
			model = HrPbindef(obj);
			key = obj;
			model.addDependant(this);
			this.makeView;
		} { |err|
			"Key % provided to Pbindef editor is of a wrong type:\n".postf(obj.asCompileString);
			err.errorString.postln;
		};
	}

	convertPattern { |pat|
		if(pat.isKindOf(HrPbindProxy)) { ^pat };
		if(pat.isKindOf(HrEventPatternProxy)) { ^pat };
		if(pat.isKindOf(EventPatternProxy)) {
			^HrEventPatternProxy(pat.source)
		};
		if(pat.isKindOf(Pbind)) { ^HrEventPatternProxy(pat) };
		// may be a valid pattern to play, but not to edit, so return nil
		^nil
	}

	clearView {
		moveFirstView.remove;
		addLastView.remove;
		subpats.reverseDo { |line|
			line.removeDependant(this);
			line.remove;
		};
		subpats = List.new;
	}

	viewDidClose {
		// if(view.parent.isClosed.not) {
		// };
		this.clearView;
		model.removeDependant(this);
		view = nil;
		this.changed(\viewDidClose);
	}

	rebuildModel { |rebuildPairs(true)|
		var pairs;
		if(rebuildPairs) {
			pairs = Array(subpats.size * 2);
			subpats.do { |subpat|
				if(subpat.text != "") {
					pairs.add(subpat.key).add(subpat.model);
				};
			};
			model.source.pairs = pairs;
		};
		// this is not a joke
		model.source.source.source = Pbind(*model.source.pairs);
	}

	update { |obj, what ... more|
		var i, new;
		if(obj === model) {
			if(obj.isKindOf(HrPbindef)) {
				this.key = obj.key;
			} {
				this.makeView;  // if we get here, it probably won't work
			};
		} {
			i = subpats.indexOf(obj) ?? { more.tryPerform(\at, 0) };
			switch(what)
			{ \addRow } {
				if(i < subpats.size) {
					(i .. subpats.size-1).do { |i|
						subpats[i].index = i+1;
						subpats[i].bounds = Rect(2, 14 + (24*(i+1)), mainView.bounds.width-4, 24);
					};
				};
				new = HrPatternLine(mainView,
					Rect(2, 14 + (24*i), mainView.bounds.width-4, 24),
					"(new)", nil, nil, i
				);
				addLastView.bounds = addLastView.bounds.moveBy(0, 24);
				subpats.insert(i, new);  // adds if i >= size
				new.addDependant(this);
				// actually, no... don't do this until you fill in a key and value
				// this.rebuildModel;
			}
			{ \deleteRow } {
				subpats[i].removeDependant(this);
				subpats[i].remove;
				subpats.removeAt(i);
				if(i < subpats.size) {
					(i .. subpats.size-1).do { |i|
						subpats[i].index = i;
						subpats[i].bounds = Rect(2, 14 + (24*i), mainView.bounds.width-4, 24);
					};
				};
				addLastView.bounds = addLastView.bounds.moveBy(0, -24);
				this.rebuildModel;
			}
			{ \reorder } {
				// more[0] is new index, more[1] is old
				// obj is nil if moving to first place
				[obj, what, more].postln;

				// using 'new' as a temp val
				new = subpats[more[1]];
				if(more[0] > more[1]) {
					// moving down
					(more[1] .. more[0]-1).do { |j|
						subpats[j] = subpats[j+1];
						subpats[j].index = j;
						subpats[j].bounds = Rect(2, 14 + (24*j), mainView.bounds.width-4, 24);
					};
				} {
					// moving up -- must go in reverse order
					(more[1], more[1]-1 .. more[0]+2).do { |j|
						subpats[j] = subpats[j-1];
						subpats[j].index = j;
						subpats[j].bounds = Rect(2, 14 + (24*j), mainView.bounds.width-4, 24);
					};
					more[0] = more[0] + 1;
				};
				subpats[more[0]] = new;
				new.index = more[0];
				new.bounds = Rect(2, 14 + (24*(more[0])), mainView.bounds.width-4, 24);
				this.rebuildModel;
			}
			{ \key } {
				if(obj.text != "") {
					this.rebuildModel;
				}
			}
			{ \source } {
				if(model.source.pairs.includes(obj.key).not) {
					this.rebuildModel;
				};
			}
			{ \viewDidClose } {
				obj.removeDependant(this);
			};
		}
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
		height = bounds.height - 4, editorBounds;

		// note, the reorderSink will be drawn outside this
		this.view = FlowView(parent,
			bounds.copy.left_(bounds.left + buttonSize + 2)
			.width_(bounds.width - buttonSize - 2),
			Point(2, 2), Point(2, 2)
		)
		.resize_(2);  // horiz. elastic, fixed to top
		background = view.background;

		index = argIndex ? 0;
		reorderDrag = HrReorderSourceView(view, buttonPoint)
		.object_("p" ++ index);

		plusBtn = Button(view, buttonPoint)
		.states_([["+"]])
		.action_({ this.changed(\addRow, index) });

		minusBtn = Button(view, buttonPoint)
		.states_([["-"]])
		.action_({ this.changed(\deleteRow, index) });

		key = argKey.asSymbol;
		// key.asString: for SwingOSC - it chokes on symbols
		label = TextField(view, 100@height).string_(key.asString).align_(\center)
		.action_({ |view|
			key = view.string.asSymbol;
			this.changed(\key, key);
		});
		label.focusLostAction = label.action;

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
		if(model.isKindOf(HrPatternProxy).not) {
			model = HrPatternProxy(model.tryPerform(\source) ? model);
		};
		if(text.isNil) {
			if(model.source.notNil) {
				text = model.source.asCompileString;
			} {
				text = "";
			};
		};
		model.addDependant(this);

		editorBounds = view.indentedRemaining;
		// width - 22: allow room for the scroller
		editorBounds = editorBounds.resizeTo(editorBounds.width - 22, height);
		editor = TextField(view, editorBounds).string_(text.asString)
		.resize_(2)  // horiz. elastic, fixed to top
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
		editor.focusLostAction = editor.action;

		// here's the outlier
		reorderSink = HrReorderSinkView(parent, Rect(
			bounds.left,
			bounds.top + (bounds.height * 0.5),
			buttonSize, buttonSize
		))
		.canReceiveDragHandler_({
			var str = View.currentDrag;
			str.isString and: {
				str[0] == $p and: { str[1..].every(_.isDecDigit) }
			}
		})
		.receiveDragHandler_({ |view|
			var i = View.currentDrag[1..].asInteger;
			if(i != index and: { i != (index + 1) }) {
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

	model_ { |obj|
		model.removeDependant(this);
		model = obj;
		model.addDependant(this);
	}

	bounds_ { |rect|
		var buttonSize = Library.at(HrPatternLine, \buttonSize) ? 20;
		view.bounds = rect.copy.left_(rect.left + buttonSize + 2)
			.width_(rect.width - buttonSize - 2);
		reorderSink.bounds = Rect(
			rect.left,
			rect.top + (rect.height * 0.5),
			buttonSize, buttonSize
		);
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

	viewDidClose {
		if(view.parent.isClosed.not) {
			reorderSink.remove;
		};
		model.removeDependant(this);
		view = nil;
		this.changed(\viewDidClose);
	}
}

HrReorderSourceView : SCViewHolder {
	var userview, dragview, <color;

	*new { |parent, bounds|
		^super.new.init(parent, bounds)
	}

	init { |parent, bounds|
		var zeroBounds = bounds.asRect.moveTo(0, 0);
		color = Color.black;
		this.view = CompositeView(parent, bounds);
		userview = UserView(view, zeroBounds)
		.background_(this.defaultBackground)
		.drawFunc_({ this.prDraw });
		this.makeView;
	}

	makeView {
		dragview = DragSource(view, userview.bounds).background_(Color.clear);
		if(GUI.id != \swing) {
			dragview.dragLabel_("Drag to reorder");
		};
	}

	object { ^dragview.object }
	object_ { |obj|
		dragview.object_(obj).string_("");
	}

	defaultBackground { ^Color(1, 0.92, 0.92) }
	background { ^userview.background }
	background_ { |color| userview.background = color }
	color_ { |newColor|
		color = newColor;
		userview.refresh;
	}

	beginDragAction { ^dragview.beginDragAction }
	beginDragAction_ { |func| dragview.beginDragAction = func }

	prDraw {
		var xs = userview.bounds.width * #[0.3, 0.5, 0.7],
		ys = userview.bounds.height * #[0.1, 0.45, 0.55, 0.9];
		Pen.color_(color)
		.moveTo(Point(xs[0], ys[1]))
		.lineTo(Point(xs[1], ys[0]))
		.lineTo(Point(xs[2], ys[1]))
		.lineTo(Point(xs[0], ys[1]))
		.fill

		.moveTo(Point(xs[0], ys[2]))
		.lineTo(Point(xs[1], ys[3]))
		.lineTo(Point(xs[2], ys[2]))
		.lineTo(Point(xs[0], ys[2]))
		.fill;		
	}
}

HrReorderSinkView : HrReorderSourceView {
	makeView {
		dragview = DragSource(view, userview.bounds).background_(Color.clear);
	}

	canReceiveDragHandler { ^dragview.canReceiveDragHandler }
	receiveDragHandler { ^dragview.receiveDragHandler }

	canReceiveDragHandler_ { |func| dragview.canReceiveDragHandler = func }
	receiveDragHandler_ { |func| dragview.receiveDragHandler = func }

	defaultBackground { ^Color(0.92, 1, 0.92) }

	prDraw {
		var xs = userview.bounds.width * #[0.1, 0.55, 0.9],
		ys = userview.bounds.height * #[0.3, 0.5, 0.7];
		Pen.color_(color)
		.moveTo(Point(xs[0], ys[1]))
		.lineTo(Point(xs[2], ys[1]))
		.stroke

		.moveTo(Point(xs[2], ys[1]))  // swing requires this
		.lineTo(Point(xs[1], ys[0]))
		.lineTo(Point(xs[1], ys[2]))
		.lineTo(Point(xs[2], ys[1]))
		.fill;		
	}
}