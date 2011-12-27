// inherit the automation etc. behavior of HrSlider
// but add a label, numberbox, auto-spec like EZSlider
// doesn't support all the formatting options of EZSlider

HrEZSlider : SCViewHolder {
	var <labelView, <sliderView, <numberView, <value, <spec, <>action;

	var isLearning, isRecording, didLearn, didRecord, learnResponder, midiResponder,
	<boundMidiArgs, <>automationData, <automationSize, oldBackground, recRoutine,
	playRoutine, isPlaying, automationArmFunc, lastArmed, isArmed, <>autoPlay,
	<>automationPlaySize, userCloseFunc;


	*new { |parent, bounds, label, controlSpec, action, initVal, initAction = false, labelWidth = 60, numberWidth = 45|
		^super.new.init(parent, bounds, label, controlSpec, action, initVal, initAction, labelWidth, numberWidth)
	}

	init { |parent, bounds, label, controlSpec, argAction, initVal, initAction, labelWidth, numberWidth|
		bounds = bounds.asRect;
		view = CompositeView(parent, bounds);
		labelView = StaticText(view, Rect(2, 0, labelWidth, bounds.height))
		.string_(label);
		sliderView = Slider(view,
			Rect(labelWidth + 6, 0,
				bounds.width - labelWidth - numberWidth - 12,
				bounds.height
			)
		).action_({ |view|
			this.valueAction = spec.map(view.value);
		});
		numberView = NumberBox(view,
			Rect(sliderView.bounds.right + 4, 0, numberWidth, bounds.height)
		).action_({ |view|
			this.valueAction = view.value;
		});
		spec = controlSpec.asSpec;
		this.value = initVal;
		action = argAction;
		if(initAction ? false) { this.doAction };
		this.prInit;  // copied from HrSlider
	}

	value_ { |val|
		value = val;
		sliderView.value = spec.unmap(val);
		numberView.value = val;
	}
	valueAction_ { |val|
		this.value = val;
		this.doAction;
	}
	doAction { action.value(this) }

	//// copy and paste - thank you, no multiple inheritance ////
	prInit
	{|argParent, argBounds|

		isLearning = false;
		isRecording = false;
		isPlaying = false;
		didLearn = false;
		didRecord = false;
		automationData = List.new;
		automationSize = inf;
		automationPlaySize = inf;
		autoPlay = true;
		isArmed = false;

		sliderView.keyDownAction_
		({|slView, char, modifiers, unicode, keycode|
			this.myKeyDown(slView, char, modifiers, unicode, keycode);
		}).onClose_({ this.doCleanUp; });


		automationArmFunc =
		{
			//"I was armed. now firing...".postln;
			isArmed = false;
			{ this.view.background = oldBackground; }.defer;
			this.startRecording;
			this.action = this.action.removeFunc(automationArmFunc);
		};

		recRoutine = Routine
		({
			automationData = List.new;
			automationSize.do
			({
				automationData.add(this.value);
				0.04.wait;
			});
			this.stopRecording;
		});

		playRoutine = Routine
		({
			var index = 0, size = min(automationData.size, automationPlaySize);
			loop
			{
				{ this.valueAction_(automationData[index]) }.defer;
				index = (index + 1) % size;
				0.04.wait;
			};
		});



	}

	onClose_
	{|argFunc|

		if(argFunc.class != FunctionList,
		{
			if(userCloseFunc.notNil,
			{
				this.view.onClose = this.view.onClose.removeFunc(userCloseFunc);
				this.view.onClose = this.view.onClose.addFunc(argFunc);
			},
			{
				this.view.onClose = this.view.onClose.addFunc(argFunc);
			});
		});

		userCloseFunc = argFunc;
	}

	myKeyDown
	{
		arg slView, char, modifiers, unicode, keycode;

		if(char == $l and: { isRecording.not } and: { isArmed.not } and: { isPlaying.not },
		{
			lastArmed = 0;
			if(isLearning and: { didLearn == false },
			{
				this.cancelLearn;
				^this;
			});

			if(isLearning.not,
			{
				this.armForLearn;
				^this;
			},
			{
				this.approveLearn;
				^this;
			});

		});

		if(char == $w and: { isLearning.not } and: { isPlaying.not },
		{
			lastArmed = 1;
			if(isRecording.not,
			{
				this.armForRecording;
			},
			{
				this.stopRecording;
			});
			^this;
		});


		if(char == $0,
		{
			if(isLearning,
			{
				this.resetLearn;
				^this;
			});

			if(isRecording,
			{
				"Recording cancelled...".postln;
				this.stopRecording;
				^this;
			});

			if(isArmed,
			{
				"Old automation data cleared...".postln;
				this.view.action = this.view.action.removeFunc(automationArmFunc);
				automationData = List.new;
				this.stopRecording;
				^this;
			});
			^this;
		});

		if(char == $p and: { automationData.size > 0 } and: { isLearning.not } and: { isArmed.not } and: { isRecording.not },
		{
			if(isPlaying,
			{
				this.stopAutomation;
			},
			{
				this.playAutomation;
			});
			^this;
		});

		//if reached here, bubble up to default action.
		this.view.defaultKeyDownAction(char, modifiers, unicode);
	}

	armForLearn
	{
		isLearning = true;
		oldBackground = this.view.background;
		this.view.background = Color.blue;
		"Twist a knob...".postln;
		learnResponder = CCResponder
		({
			arg src, chan, num, value;
			boundMidiArgs = [src, chan, num];
			{ this.view.valueAction_(value/127); }.defer;
			didLearn = true;
		});
	}

	cancelLearn
	{
		isLearning = false;
		boundMidiArgs = nil;
		this.view.background = oldBackground;
		learnResponder.remove;
		"Learning cancelled...".postln;
	}

	approveLearn
	{
		this.view.background = oldBackground;
		learnResponder.remove;
		isLearning = false;
		if(didLearn,
		{
			midiResponder.remove;
			midiResponder =
			CCResponder({
				arg src, chan, num, value;
				{ this.view.valueAction_(value/127); }.defer;
			}, *boundMidiArgs);
		});
	}

	resetLearn
	{
		boundMidiArgs = nil;
		midiResponder.remove;
		learnResponder.remove;
		{ this.view.background = oldBackground; }.defer;
		"Old midi learn info cleared...".postln;
		isLearning = false;
		didLearn = false;
	}

	armForRecording
	{
		if(isArmed.not,
		{
			isArmed = true;
			oldBackground = this.view.background;
			{ this.view.background = Color.new255(255, 69, 0); }.defer;
			//"adding func".postln;
			this.view.action = this.view.action.addFunc(automationArmFunc);

		},
		{
			isArmed = false;
			{ this.view.background = oldBackground; }.defer;
			this.view.action = this.view.action.removeFunc(automationArmFunc);
		})
	}

	startRecording
	{
		isArmed = false;
		isRecording = true;
		oldBackground = this.view.background;
		{ this.view.background = Color.red; }.defer;
		recRoutine.reset;
		recRoutine.play(AppClock);
	}

	stopRecording
	{
		isRecording = false;
		isArmed = false;
		{ this.view.background = oldBackground; }.defer;
		recRoutine.stop;
		if(automationData.size > 0, { if(autoPlay, { this.playAutomation; }); });

	}

	playAutomation
	{
		playRoutine.reset;
		playRoutine.play(AppClock);
		isPlaying = true;
	}

	stopAutomation
	{
		playRoutine.stop;
		isPlaying = false;
	}

	doCleanUp
	{
		recRoutine.stop;
		playRoutine.stop;
		learnResponder.remove;
		midiResponder.remove;

	}
	/////

	boundMidiArgs_
	{|argBM|

		if(argBM.notNil,
		{
			boundMidiArgs = argBM;
			oldBackground = this.view.background;
			didLearn = true;
			this.approveLearn;
		});
	}
}
