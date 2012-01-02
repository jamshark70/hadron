HrCtlEnv : HrCtlMod {
	var <>spec,
	loopNode, releaseNode,
	specMin, specMax, specWarp, specStep,
	timeScaleView, timeScale = 1;
	*initClass {
		this.addHadronPlugin;
	}
	*height { ^350 }
	*numOuts { ^1 }
	// copy and paste programming...
	init
	{
		var expWarpCheck = {
			spec.minval.sign != spec.maxval.sign
			or: { spec.minval.sign == 0 or: { spec.maxval.sign == 0 } }
		};

		window.background_(Color.gray(0.9));
		prOutBus = Bus.control(Server.default, 1);
		helpString = "Envelope editor, to map to a modulatable control.";

		// wrongly named vars, to avoid tacking new vars onto a bunch of unused ones
		postOpText = HrEnvelopeView(window, Rect(10, 10, 430, 200))
		.env_(Env.adsr)
		.action_({ |view|
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
				synthInstance.set(\env, view.value.asArray.extend(48, 0))
			};
		});
		postOpText.deleteAction = postOpText.insertAction;

		modControl = HadronModTargetControl(window, Rect(10, 250, 430, 20), parentApp);
		modControl.addDependant(this);

		evalButton = Button(window, Rect(10, 220, 80, 20)).states_([["Trigger"]])
		.action_({
			if(synthInstance.notNil) { synthInstance.set(\t_trig, 1) };
		});

		startButton = Button(window, Rect(100, 220, 80, 20)).states_([["Start"],["Stop"]])
		.value_(modControl.currentSelPlugin.notNil and: {
			modControl.currentSelParam.notNil
		})
		.action_({|btn|
			// synthInstance.run(btn.value == 1)
			if(btn.value == 1) { modControl.map(prOutBus) }
			{ modControl.unmap };
		});

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
		});

		spec = ControlSpec.new;
		StaticText(window, Rect(10, 275, 80, 20)).string_("map min");
		StaticText(window, Rect(210, 275, 80, 20)).string_("map max");
		StaticText(window, Rect(10, 300, 80, 20)).string_("map warp");
		StaticText(window, Rect(210, 300, 80, 20)).string_("map step");

		specMin = NumberBox(window, Rect(100, 275, 100, 20))
		.value_(spec.minval)
		.action_({ |view|
			if(spec.warp.class != ExponentialWarp or: { expWarpCheck.value }) {
				spec.minval = view.value; this.makeSynth
			} {
				parentApp.displayStatus("Invalid warp: Exponential warp endpoints must be the same sign and nonzero", -1);
			};
		});
		specMax = NumberBox(window, Rect(300, 275, 100, 20))
		.value_(spec.maxval)
		.action_({ |view|
			if(spec.warp.class != ExponentialWarp or: { expWarpCheck.value }) {
				spec.maxval = view.value; this.makeSynth
			} {
				parentApp.displayStatus("Invalid warp: Exponential warp endpoints must be the same sign and nonzero", -1);
			};
		});
		specWarp = TextField(window, Rect(100, 300, 100, 20))
		.string_("lin")
		.action_({ |view|
			var warp, continue = true;
			try {
				warp = view.string.interpret;
				if(warp.respondsTo(\asWarp).not) {
					Error("Warp must be number or symbol").throw;
				};
				warp = warp.asWarp(spec);  // throws error if a wrong symbol
				if(warp.class == ExponentialWarp and: { expWarpCheck.value }) {
					Error("Exponential warp endpoints must be the same sign and nonzero").throw
				};
			} { |err|
				if(err.isKindOf(Exception)) {
					continue = false;
					err.reportError;
					defer { parentApp.displayStatus("Invalid warp: " ++ err.errorString, -1) };
				}
			};
			if(continue) {
				spec.warp = warp;
				this.makeSynth;
			};
		});
		specStep = NumberBox(window, Rect(300, 300, 100, 20))
		.action_({ |view| spec.step = view.value; this.makeSynth });

		timeScaleView = HrEZSlider(window, Rect(10, 325, 430, 20), "time scale", #[0.01, 20, \exp],
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
				{|argg| spec = argg.interpret },
				{|argg| postOpText.env_(argg.interpret).doAction },
				{|argg|
					timeScale = argg.interpret;
					timeScaleView.value_(timeScale).doAction
				},
				{|argg| modControl.putSaveValues(argg); },
				{|argg| startButton.valueAction_(argg); }
			];
	}

	synthArgs {
		^[inBus0: inBusses[0], outBus0: outBusses[0], prOutBus: prOutBus,
			timeScale: timeScale]
	}

	makeSynthDef {
		SynthDef("HrCtlEnv" ++ uniqueID, { |t_trig, inBus0, outBus0, prOutBus, timeScale = 1|
			var env = NamedControl.kr(\env, (0 ! 48).overWrite(Env.adsr.asArray)),
			audioTrig = In.ar(inBus0, 1),
			eg = EnvGen.kr(env,
				t_trig,  // + RunningSum.ar(max(0, audioTrig),
				//numsamp: Server.default.options.blockSize)
				timeScale: timeScale
			);
			// A2K takes only the first sample, missing mid-block trigs
			Out.kr(prOutBus, spec.map(eg));
			Out.ar(outBus0, /*audioTrig +*/ K2A.ar(t_trig));
		}).add;
	}
}