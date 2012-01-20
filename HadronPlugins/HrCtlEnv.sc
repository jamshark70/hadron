HrCtlEnv : HrCtlMod {
	var <>spec,
	loopNode, releaseNode,
	specView, timeScaleView, timeScale = 1;

	*initClass {
		this.addHadronPlugin;
	}
	*height { |extraArgs| ^350 + (25 * this.shouldWatch(extraArgs).binaryValue) }
	// default is NOT to watch
	*shouldWatch { |argExtraArgs|
		^argExtraArgs.size >= 1 and: {
			argExtraArgs[0] == "1" or: { argExtraArgs[0] == "true" }
		}
	}

	// left = env, right = trig (true for HrAudioEnv also)
	*numOuts { ^2 }

	// copy and paste programming...
	init
	{
		var adjustY = 0, specErrResp;

		if(extraArgs.size >= 2 and: {
			extraArgs[1].size > 0 and: { extraArgs[1].asFloat > 0 }
		}) {
			pollRate = extraArgs[1].asFloat;
		} {
			pollRate = defaultPollRate;
		};
		numChannels = 1;  // always 1 for an enveloper

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
		.curveAction_({ |view|
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
			modControl = HadronModTargetControl(window, Rect(10, 250, 430, 20), parentApp, this);
			modControl.addDependant(this);

			startButton = Button(window, Rect(100, 220, 80, 20))
			.states_([["Start"],["Stop"]])
			.value_(binaryValue(modControl.currentSelPlugin.notNil and: {
				modControl.currentSelParam.notNil
			}))
			.action_({|btn|
				if(btn.value == 1) {
					modControl.map(prOutBus);
					isMapped = true;
					synthInstance.set(\pollRate, pollRate * (watcher.notNil.binaryValue));
				} {
					modControl.unmap;
					isMapped = false;
					synthInstance.set(\pollRate, 0);
				};
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

		specView = HrSpecEditor(window, Rect(5, 270 - adjustY, window.bounds.width - 10, 50), 5@5)
		.value_(spec)
		.action_({ |view, paramName|
			spec = view.value;
			if(paramName == \warp) {
				this.makeSynth;
			} {
				synthInstance.set(paramName, spec.perform(paramName));
			};
		});
		specErrResp = SimpleController(specView).put(\message, { |obj, what, string, mood = 0|
			defer {
				parentApp.displayStatus(string, mood);
			};
		})
		.put(\viewDidClose, { specErrResp.remove });

		timeScaleView = HrEZSlider(window, Rect(10, 325 - adjustY, 430, 20), "time scale", #[0.01, 20, \exp],
			{ |view|
				timeScale = view.value;
				if(synthInstance.notNil) { synthInstance.set(\timeScale, timeScale) };
			}, 1, initAction: true);

		if(this.shouldWatch) {
			pollRateView = HrEZSlider(
				window, 
				Rect(10, timeScaleView.bounds.top + 25, 430, 20),
				"update rate", [1, 25], { |view|
					pollRate = view.value;
					if(synthInstance.notNil) {
						synthInstance.set(\pollRate, pollRate * (watcher.notNil.binaryValue));
					};
				}, pollRate, labelWidth: 100, numberWidth: 45
			);
		};

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
					specView.value = spec;
					// specMin.value = spec.minval;
					// specMax.value = spec.maxval;
					// specWarp.string = spec.warp.asSpecifier.asString;
					// specStep.value = spec.step;
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

		modMapSets.putAll((
			timeScale: { |val|
				timeScale = val;
				defer { timeScaleView.value = val };
			}
		));

		modGets.putAll((
			t_trig: { 0 },
			timeScale: { timeScale }
		));

		if(this.shouldWatch) {
			watcher = OSCresponderNode(Server.default.addr, '/modValue', { |time, resp, msg|
				if(msg[2] == uniqueID) {
					modControl.updateMappedGui(msg[3]);
				}
			}).add;
		};
	}

	synthArgs {
		^[inBus0: inBusses[0], outBus0: outBusses[0], prOutBus: prOutBus,
			timeScale: timeScale, env: postOpText.value,
			minval: spec.minval, maxval: spec.maxval, step: spec.step,
			pollRate: pollRate * isMapped.binaryValue * (watcher.notNil.binaryValue)
		] ++ this.getMapModArgs
	}

	makeSynthDef {
		SynthDef("HrCtlEnv" ++ uniqueID, { |t_trig, inBus0, outBus0, prOutBus,
			minval = 0, maxval = 1, step = 0, timeScale = 1, pollRate = 0|
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
			eg = localSpec.map(eg);
			SendReply.kr(Impulse.kr(pollRate), '/modValue', eg, uniqueID);
			Out.kr(prOutBus, eg);
			Out.ar(outBus0, [K2A.ar(eg), audioTrig + K2A.ar(t_trig)]);
		}).add;
	}

	// cleanUp {
	// 	super.cleanUp;
	// }
}

HrAudioEnv : HrCtlEnv {
	*initClass {
		this.addHadronPlugin;
	}

	*height { ^325 }
	modulatesOthers { ^false }

	init {
		super.init;
		saveGets.removeAt(4); saveGets.removeAt(3);  // hackity hack hack hack
		saveSets.removeAt(4); saveSets.removeAt(3);
	}
	// CAN'T watch
	*shouldWatch { ^false }

	synthArgs {
		^[inBus0: inBusses[0], outBus0: outBusses[0], outBus1: outBusses[1],
			timeScale: timeScale, env: postOpText.value,
			minval: spec.minval, maxval: spec.maxval, step: spec.step
		] ++ this.getMapModArgs
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