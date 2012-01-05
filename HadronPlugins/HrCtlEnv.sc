HrCtlEnv : HrCtlMod {
	var <>spec,
	loopNode, releaseNode,
	specMin, specMax, specWarp, specStep,
	badSpecRoutines,
	timeScaleView, timeScale = 1;
	*initClass {
		this.addHadronPlugin;
	}
	*height { ^350 }
	*numOuts { ^2 }
	// copy and paste programming...
	init
	{
		var expWarpIsBad = {
			specMin.value.sign != specMax.value.sign
			or: { specMin.value == 0 or: { specMax.value == 0 } }
		}, adjustY = 0;

		badSpecRoutines = ();

		window.background_(Color.gray(0.9));
		prOutBus = Bus.control(Server.default, 1);
		helpString = "Envelope editor, to map to a modulatable control.";

		// wrongly named vars, to avoid tacking new vars onto a bunch of unused ones
		postOpText = HrEnvelopeView(window, Rect(10, 10, 430, 200))
		.env_(Env.adsr)
		.action_({ |view|
			// release and loop nodes didn't change here
			// so it's OK to send the new breakpoint data
			if(synthInstance.notNil) {
				synthInstance.set(\env, view.value.asArray.extend(48, 0))
			};
		})
		.insertAction_({ |view|
			loopNode.items = ["None"] ++ Array.fill(view.curves.size - 1, _.asString);
			releaseNode.items = loopNode.items;
			loopNode.value = (view.loopNode ? -1) + 1;
			releaseNode.value = (view.releaseNode ? -1) + 1;
			if(synthInstance.notNil) {
				// release and loop nodes are not modulatable in the same synth
				// so we have to kill and restart the synth
				// but don't need a new synthdef -- (false)
				this.makeSynth(false);
			};
		});
		postOpText.deleteAction = postOpText.insertAction;

		evalButton = Button(window, Rect(10, 220 - adjustY, 80, 20)).states_([["Trigger"]])
		.action_({
			if(synthInstance.notNil) { synthInstance.set(\t_trig, 1) };
		});

		if(this.modulatesOthers) {
			modControl = HadronModTargetControl(window, Rect(10, 250, 430, 20), parentApp);
			modControl.addDependant(this);

			startButton = Button(window, Rect(100, 220, 80, 20))
			.states_([["Start"],["Stop"]])
			.value_(modControl.currentSelPlugin.notNil and: {
				modControl.currentSelParam.notNil
			})
			.action_({|btn|
				if(btn.value == 1) { modControl.map(prOutBus) }
				{ modControl.unmap };
			});
		} {
			adjustY = 25;
		};

		StaticText(window, Rect(190, 220, 35, 20)).string_("loop");
		loopNode = PopUpMenu(window, Rect(235, 220, 65, 20))
		.items_(["None", "0", "1", "2"])
		.action_({ |view|
			if(view.value == 0) {
				postOpText.loopNode = nil
			} {
				postOpText.loopNode = view.value - 1;
			};
			postOpText.updateEnvView;
			this.makeSynth(false);
		});
		StaticText(window, Rect(310, 220, 35, 20)).string_("rel");
		releaseNode = PopUpMenu(window, Rect(345, 220, 65, 20))
		.items_(loopNode.items)
		.value_(3)  // per Env.adsr, default node is 2 or item index 3
		.action_({ |view|
			if(view.value == 0) {
				postOpText.releaseNode = nil
			} {
				postOpText.releaseNode = view.value - 1;
			};
			postOpText.updateEnvView;
			this.makeSynth(false);
		});

		spec = HrControlSpec.new;
		StaticText(window, Rect(10, 275 - adjustY, 80, 20)).string_("map min");
		StaticText(window, Rect(210, 275 - adjustY, 80, 20)).string_("map max");
		StaticText(window, Rect(10, 300 - adjustY, 80, 20)).string_("map warp");
		StaticText(window, Rect(210, 300 - adjustY, 80, 20)).string_("map step");

		specMin = NumberBox(window, Rect(100, 275 - adjustY, 100, 20))
		.value_(spec.minval)
		.maxDecimals_(5)
		.action_({ |view|
			if(spec.warp.class == ExponentialWarp and: expWarpIsBad) {
				parentApp.displayStatus("Invalid warp: Exponential warp endpoints must be the same sign and nonzero", -1);
				badSpecRoutines[\minval] ?? {
					badSpecRoutines[\minval] = Routine({
						var colors = Pseq([Color(1.0, 0.86, 0.86), Color.white], inf).asStream;
						loop {
							view.background = colors.next;
							0.75.wait;
						};
					}).play(AppClock);
				};
			} {
				spec.minval = view.value;
				synthInstance.set(\minval, spec.minval);
				this.prStopRoutines;
				badSpecRoutines[\minval] = nil;
				view.background = Color.white;
			};
		});
		specMax = NumberBox(window, Rect(300, 275 - adjustY, 100, 20))
		.value_(spec.maxval)
		.maxDecimals_(5)
		.action_({ |view|
			if(spec.warp.class == ExponentialWarp and: expWarpIsBad) {
				parentApp.displayStatus("Invalid warp: Exponential warp endpoints must be the same sign and nonzero", -1);
				badSpecRoutines[\maxval] ?? {
					badSpecRoutines[\maxval] = Routine({
						var colors = Pseq([Color(1.0, 0.86, 0.86), Color.white], inf).asStream;
						loop {
							view.background = colors.next;
							0.75.wait;
						};
					}).play(AppClock);
				};
			} {
				spec.maxval = view.value;
				synthInstance.set(\maxval, spec.maxval);
				this.prStopRoutines;
				badSpecRoutines[\maxval] = nil;
				view.background = Color.white;
			};
		});
		specWarp = TextField(window, Rect(100, 300 - adjustY, 100, 20))
		.string_("lin")
		.action_({ |view|
			var warp, continue = true;
			try {
				warp = view.string;
				if(warp.every(_.isAlpha)) {
					warp = warp.asSymbol;
				} {
					warp = warp.asFloat;
					if(warp == 0 and: {
						view.string.any { |char|
							char.isDecDigit.not and: { char != $. }
						}
					}) {
						// this, of course, may throw a different error
						// which is why all of this is in a try{} block
						warp = view.string.interpret
					};
				};
				if(warp.respondsTo(\asWarp).not) {
					Error("Warp must be number, symbol or a valid SC expression").throw;
				};
				warp = warp.asWarp(spec);  // throws error if a wrong symbol
				if(warp.class == ExponentialWarp and: expWarpIsBad) {
					Error("Exponential warp endpoints must be the same sign and nonzero").throw
				};
			} { |err|
				if(err.isKindOf(Exception)) {
					continue = false;
					err.reportError;
					defer { parentApp.displayStatus("Invalid warp: " ++ err.errorString, -1) };
					badSpecRoutines[\warp] ?? {
						badSpecRoutines[\warp] = Routine({
							var colors = Pseq([Color(1.0, 0.86, 0.86), Color.white], inf).asStream;
							loop {
								view.background = colors.next;
								0.75.wait;
							};
						}).play(AppClock);
					};
				};
			};
			if(continue) {
				spec.warp = warp;
				this.makeSynth;
				this.prStopRoutines;
				badSpecRoutines[\warp] = nil;
				view.background = Color.white;
			};
		});
		specStep = NumberBox(window, Rect(300, 300 - adjustY, 100, 20))
		.maxDecimals_(5)
		.action_({ |view| spec.step = view.value; synthInstance.set(\step, spec.step) });

		timeScaleView = HrEZSlider(window, Rect(10, 325 - adjustY, 430, 20), "time scale", #[0.01, 20, \exp],
			{ |view|
				timeScale = view.value;
				if(synthInstance.notNil) { synthInstance.set(\timeScale, timeScale) };
			}, 1, initAction: true);

		this.makeSynth;

		saveGets =
			[
				{ spec.asCompileString },
				{ postOpText.value.asCompileString },
				{ timeScale.asString },
				{ modControl.getSaveValues; },
				{ startButton.value; }
			];

		saveSets =
			[
				{|argg|
					spec = argg.interpret;
					specMin.value = spec.minval;
					specMax.value = spec.maxval;
					specWarp.string = spec.warp.asSpecifier.asString;
					specStep.value = spec.step;
				},
				{|argg|
					var env = argg.interpret;
					loopNode.items = ["None"] ++ Array.fill(env.curves.size, _.asString);
					releaseNode.items = loopNode.items;
					loopNode.value = (env.loopNode ? -1) + 1;
					releaseNode.value = (env.releaseNode ? -1) + 1;
					postOpText.env = env;
					this.makeSynth(false);
				},
				{|argg|
					timeScale = argg.interpret;
					timeScaleView.value_(timeScale).doAction
				},
				{|argg| modControl.putSaveValues(argg); },
				{|argg| startButton.valueAction_(argg); }
			];

		modSets.putAll((
			t_trig: { |val| synthInstance.set(\t_trig, val) },
			timeScale: { |val|
				timeScale = val;
				defer { timeScaleView.value = val };
				synthInstance.set(\timeScale, val);
			}
		));

		modGets.putAll((
			t_trig: { 0 },
			timeScale: { timeScale }
		));
	}

	synthArgs {
		^[inBus0: inBusses[0], outBus0: outBusses[0], prOutBus: prOutBus,
			timeScale: timeScale, env: postOpText.value,
			minval: spec.minval, maxval: spec.maxval, step: spec.step
		]
	}

	makeSynthDef {
		SynthDef("HrCtlEnv" ++ uniqueID, { |t_trig, inBus0, outBus0, prOutBus,
			minval = 0, maxval = 1, step = 0, timeScale = 1|
			var env = NamedControl.kr(\env, (0 ! 48).overWrite(Env(#[0, 0], #[1]).asArray)),
			audioTrig = InFeedback.ar(inBus0, 1),
			eg = EnvGen.kr(env,
				// A2K takes only the first sample, missing mid-block trigs
				t_trig + RunningSum.ar(max(0, audioTrig),
					numsamp: Server.default.options.blockSize),
				timeScale: timeScale
			),
			localSpec = spec.copy
			.minval_(minval)  // replace hardcoded endpoints with control inputs
			.maxval_(maxval)
			.step_(step);
			Out.kr(prOutBus, localSpec.map(eg));
			Out.ar(outBus0, audioTrig + K2A.ar(t_trig));
		}).add;
	}

	cleanUp {
		this.prStopRoutines;
		super.cleanUp;
	}

	// this should be called only when the spec is totally valid
	// so it clears all flashy-flashy routines
	// and resets the 3 relevant background colors
	prStopRoutines {
		badSpecRoutines.do(_.stop);
		[specMin, specMax, specWarp].do { |view|
			if(view.notClosed) {
				view.background = Color.white;
			};
		};
	}
}

HrAudioEnv : HrCtlEnv {
	*initClass {
		this.addHadronPlugin;
	}

	// numOuts still 2: left = env, right = trig

	*height { ^325 }
	modulatesOthers { ^false }

	init {
		super.init;
		saveGets.removeAt(4); saveGets.removeAt(3);  // hackity hack hack hack
		saveSets.removeAt(4); saveSets.removeAt(3);
	}

	synthArgs {
		^[inBus0: inBusses[0], outBus0: outBusses[0], outBus1: outBusses[1],
			timeScale: timeScale, env: postOpText.value,
			minval: spec.minval, maxval: spec.maxval, step: spec.step
		]
	}

	makeSynthDef {
		SynthDef("HrAudioEnv" ++ uniqueID, { |t_trig, inBus0, outBus0, outBus1,
			minval = 0, maxval = 1, step = 0, timeScale = 1|
			var env = NamedControl.kr(\env, (0 ! 48).overWrite(Env(#[0, 0], #[1]).asArray)),
			audioTrig = InFeedback.ar(inBus0, 1),
			eg = EnvGen.ar(env,
				// A2K takes only the first sample, missing mid-block trigs
				t_trig + RunningSum.ar(max(0, audioTrig),
					numsamp: Server.default.options.blockSize),
				timeScale: timeScale
			),
			localSpec = spec.copy
			.minval_(minval)  // replace hardcoded endpoints with control inputs
			.maxval_(maxval)
			.step_(step);
			Out.ar(outBus0, localSpec.map(eg));
			Out.ar(outBus1, audioTrig + K2A.ar(t_trig));
		}).add;
	}

	notifyPlugAdd {}  // override parent - I don't have a mod control
	notifyPlugKill {}
	wakeFromLoad {}
}