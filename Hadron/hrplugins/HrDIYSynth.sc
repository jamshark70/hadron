HrDIYSynth : HadronPlugin
{
	var sDef, codeView, wrapFunc;

	*new
	{|argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|

		var numIns = 2;
		var numOuts = 2;
		var bounds = Rect((Window.screenBounds.width - 450).rand, (Window.screenBounds.height - 400).rand, 450, 400);
		var name = "HrDIYSynth";

		if(argExtraArgs.size >= 2) {
			numOuts = argExtraArgs[1].asInteger;
		};
		if(argExtraArgs.size >= 1) {
			numIns = argExtraArgs[0].asInteger;
		};

		^super.new(argParentApp, name, argIdent, argUniqueID, argExtraArgs, bounds, numIns, numOuts, argCanvasXY).init;
	}

	init
	{
		window.background_(Color.gray(0.8));
		helpString = "'M' audio inputs, given as args to function. Return 'N' channels of audio M and N are creation args.";

		{
			codeView = TextView(window, Rect(10, 10, 430, 350))
			.string_("{ arg input; input; }")
			.usesTabToFocusNextView_(false)
			.enterInterpretsSelection_(false)
			.editable_(true);

			if(GUI.id == \swing, { SwingOSC.default.sync; });

			this.redefineSynth(codeView.string.interpret);
		}.fork(AppClock);

		Button(window, Rect(10, 370, 80, 20)).states_([["Evaluate"]])
		.action_
		({
			this.redefineSynth(codeView.string.interpret);
		});

		//this.redefineSynth(codeView.string.interpret);

		saveGets =
			[
				{ codeView.string.replace("\n", 30.asAscii); }
			];

		saveSets =
			[
				{|argg| codeView.string_(argg.replace(30.asAscii.asString, "\n")); }
			]

	}

	// expose to the plugin superclass interface
	// it expects makeSynth, not redefineSynth
	makeSynth {
		if(this.canCallOS) {
			this.redefineSynth(codeView.string.interpret);
		} {
			AppClock.sched(0, {
				this.redefineSynth(codeView.string.interpret);
				nil
			})
		};
	}

	releaseSynth {
		if(synthInstance.notNil, {
			synthInstance.set(\masterGate, 0);
			synthInstance = nil;
		});
	}

	redefineSynth
	{|argWrapFunc|
		var shouldPlay = true;

		try {
			sDef =
			SynthDef("hrDIYSynth"++uniqueID,
				{
					arg inBus0, /*inBus1,*/ outBus0, /*outBus1,*/ masterGate = 1;
					var inputs = InFeedback.ar(inBus0, inBusses.size).asArray;

					var sound;

					sound = SynthDef.wrap(argWrapFunc, [0], [inputs])
					* EnvGen.kr(Env.asr(0.02, 1, 0.02), masterGate, doneAction: 2);

					if(sound.isSequenceableCollection.not) {
						sound = [sound];
					};
					if(sound.size < outBusses.size) {
						sound = sound.wrapExtend(outBusses.size);
					};

					Out.ar(outBus0, sound);
				});
		} { |err|
			if(err.isKindOf(Exception)) {
				shouldPlay = false;
				err.reportError;
				defer { parentApp.displayStatus(err.errorString, -1) };
			};
		};

		if(shouldPlay) {
			fork {
				sDef.add;

				Server.default.sync;

				this.releaseSynth;
				synthInstance =
				Synth(sDef.name,
					[
						\inBus0, inBusses[0],
						// \inBus1, inBusses[1],
						\outBus0, outBusses[0],
						// \outBus1, outBusses[1]
					], target: group);
			};
		};
	}

	wakeFromLoad
	{
		{
			if(GUI.id == \swing, { SwingOSC.default.sync; });
			this.redefineSynth(codeView.string.interpret);
		}.fork(AppClock);
	}

	updateBusConnections
	{
		synthInstance.set(\inBus0, inBusses[0], \inBus1, inBusses[1], \outBus0, outBusses[0], \outBus1, outBusses[1]);
	}

	cleanUp
	{
		this.releaseSynth;
	}
}