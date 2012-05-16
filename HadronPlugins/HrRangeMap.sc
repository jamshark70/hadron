HrRangeMap : HadronPlugin {
	var inSpec, outSpec;
	var inSpecView, outSpecView, inMod = 0, inModButton;
	var modControl, modSlider, isMapped = false, prOutBus, watcher, replyID, <pollRate;

	*initClass {
		this.addHadronPlugin;
	}

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, "HrRangeMap", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 410).rand, (Window.screenBounds.height - 245).rand, 410, 245), 1, 1, argCanvasXY).init
	}

	init {
		var inColor = Color(1, 1, 0.8), outColor = Color(0.8, 1, 0.8);
		var inSpecErrResp, outSpecErrResp;

		inSpec = HrControlSpec.new;
		outSpec = HrControlSpec.new;
		pollRate = HrCtlMod.defaultPollRate;

		prOutBus = Bus.control(Server.default, 1);

		inModButton = Button(window, Rect(10, 10, 125, 20))
		.states_([["Using modulator"], ["Using input cable"]])
		.action_({ |view|
			inMod = view.value;
			synthInstance.set(\useAudioIn, inMod);
			modSlider.visible = (inMod == 0);
		});

		modSlider = HrEZSlider(window, Rect(10, 35, 390, 20), "Mod source", #[0, 1],
			action: { |view| synthInstance.set(\modValue, view.value) }
		);

		StaticText(window, Rect(5, 60, 400, 20))
		.background_(inColor)
		.align_(\center)
		.string_("Input spec");

		inSpecView = HrSpecEditor(window, Rect(5, 80, 400, 50), labelPrefix: "in")
		.background_(inColor)
		.action_({ |view, paramName|
			inSpec = view.value;
			modSlider.spec = inSpec;
			if(paramName == \warp) {
				this.makeSynth;
			} {
				synthInstance.set("in" ++ paramName, inSpec.perform(paramName));
			};
		});
		inSpecErrResp = SimpleController(inSpecView).put(\message, { |obj, what, string, mood = 0|
			defer {
				parentApp.displayStatus(string, mood);
			};
		})
		.put(\viewDidClose, { inSpecErrResp.remove });

		StaticText(window, Rect(5, 135, 400, 20))
		.background_(outColor)
		.align_(\center)
		.string_("Output spec");

		outSpecView = HrSpecEditor(window, Rect(5, 155, 400, 50), labelPrefix: "out")
		.background_(outColor)
		.action_({ |view, paramName|
			outSpec = view.value;
			if(paramName == \warp) {
				this.makeSynth;
			} {
				synthInstance.set("out" ++ paramName, outSpec.perform(paramName));
			};
		});
		outSpecErrResp = SimpleController(outSpecView).put(\message, { |obj, what, string, mood = 0|
			defer {
				parentApp.displayStatus(string, mood);
			};
		})
		.put(\viewDidClose, { outSpecErrResp.remove });

		modControl = HadronModTargetControl(window, Rect(10, 215, 390, 20), parentApp, this);
		modControl.addDependant(this);

		this.makeSynth;

		watcher = OSCresponderNode(Server.default.addr, '/modValue', { |time, resp, msg|
			if(msg[2] == replyID) {
				modControl.updateMappedGui(msg[3]);
			}
		}).add;

		saveGets =
			[
				{ [inSpec, outSpec].asCompileString },
				{ inMod },
				{ modControl.getSaveValues; },
				{ modSlider.value }
			];

		saveSets =
			[
				{ |argg|
					var newInSpec, newOutSpec, warpChanged;
					#newInSpec, newOutSpec = argg.interpret;
					warpChanged = (inSpec.warp != newInSpec.warp) or: {
						outSpec.warp != newOutSpec.warp
					};
					inSpec = newInSpec;
					outSpec = newOutSpec;
					inSpecView.value = inSpec;
					outSpecView.value = outSpec;
					modSlider.spec = inSpec;
					if(warpChanged) {
						this.makeSynth;
					} {
						synthInstance.set(*this.synthArgs);
					};
				},
				{ |argg|
					inMod = argg;
					inModButton.value = inMod;
					modSlider.visible = (inMod == 0);
					synthInstance.set(\useAudioIn, inMod);
				},
				{ |argg|
					modControl.putSaveValues(argg);
					if(inMod == 0) {
						isMapped = modControl.map(prOutBus)
					} {
						isMapped = false;
					};
				},
				{ |argg|
					modSlider.value = argg;
					if(isMapped.not) {
						synthInstance.set(\modValue, argg)
					} {
						prOutBus.set(argg)
					};
				}
			];

		modSets.putAll((
			modValue: { |val|
				synthInstance.set(\modValue, val);
				defer { if(inMod == 0) { modSlider.value = val } };
			},
			inminval: { |val|
				synthInstance.set(\inminval, val);
				defer { modSlider.value = val };
			},
			inmaxval: { |val|
				synthInstance.set(\inmaxval, val);
				defer { modSlider.value = val };
			},
			instep: { |val|
				synthInstance.set(\instep, val);
				defer { modSlider.value = val };
			},
			outminval: { |val|
				synthInstance.set(\outminval, val);
				defer { modSlider.value = val };
			},
			outmaxval: { |val|
				synthInstance.set(\outmaxval, val);
				defer { modSlider.value = val };
			},
			outstep: { |val|
				synthInstance.set(\outstep, val);
				defer { modSlider.value = val };
			}
		));

		modMapSets.putAll((
			modValue: { |val|
				defer { if(inMod == 0) { modSlider.value = val } };
			},
			inminval: { |val|
				defer { modSlider.value = val };
			},
			inmaxval: { |val|
				defer { modSlider.value = val };
			},
			instep: { |val|
				defer { modSlider.value = val };
			},
			outminval: { |val|
				defer { modSlider.value = val };
			},
			outmaxval: { |val|
				defer { modSlider.value = val };
			},
			outstep: { |val|
				defer { modSlider.value = val };
			}
		));

		modGets.putAll((
			modValue: { modSlider.value },
			inminval: { modSlider.value },
			inmaxval: { modSlider.value },
			instep: { modSlider.value },
			outminval: { modSlider.value },
			outmaxval: { modSlider.value },
			outstep: { modSlider.value }
		));
	}

	synthArgs {
		^[inBus0: inBusses[0], prOutBus: prOutBus,
		outBus0: outBusses[0], useAudioIn: inMod, modValue: modSlider.value,
		inminval: inSpec.minval, inmaxval: inSpec.maxval, instep: inSpec.step,
		outminval: outSpec.minval, outmaxval: outSpec.maxval, outstep: outSpec.step,
		pollRate: pollRate * isMapped.binaryValue * (watcher.notNil.binaryValue)
		] ++ this.getMapModArgs
	}

	makeSynthDef {
		replyID = UniqueID.next;
		SynthDef("HrRangeMap"++uniqueID, { |prOutBus, inBus0, outBus0, modValue, pollRate = 0,
			inminval = 0, inmaxval = 1, instep = 0,
			outminval = 0, outmaxval = 1, outstep = 0,
			useAudioIn = 0|
			var input = InFeedback.ar(inBus0),
			localSpec;
			modValue = K2A.ar(modValue);
			input = (input - modValue) * Lag.kr(useAudioIn.clip(0, 1), 0.05) + modValue;
			localSpec = inSpec.copy
			.minval_(inminval)  // replace hardcoded endpoints with control inputs
			.maxval_(inmaxval)
			.step_(instep);
			input = localSpec.unmap(input);
			localSpec = outSpec.copy
			.minval_(outminval)
			.maxval_(outmaxval)
			.step_(outstep);
			input = localSpec.map(input);
			Out.ar(outBus0, input);
			input = A2K.kr(input);
			SendReply.kr(Impulse.kr(pollRate), '/modValue', input, replyID);
			Out.kr(prOutBus, input);
		}).add;
	}

	update { |obj, what, argument, oldplug, oldparam|
		if(#[currentSelPlugin, currentSelParam].includes(what)) {
			if(argument.notNil) {
				modControl.unmap(oldplug, oldparam);
				isMapped = modControl.map(prOutBus);
				synthInstance.set(\pollRate,
					pollRate * isMapped.binaryValue * (watcher.notNil.binaryValue)
				);
				// defer { startButton.value = isMapped.binaryValue };
			} {
				modControl.unmap(oldplug, oldparam);
				synthInstance.set(\pollRate, 0);
				isMapped = false;
				// defer { startButton.value = 0 };
			};
		};
	}

	updateBusConnections {
		synthInstance.set(\inBus0, inBusses[0], \outBus0, outBusses[0]);
	}

	wakeFromLoad {
		modControl.doWakeFromLoad;
	}

	notifyPlugAdd { |argPlug|
		modControl.plugAdded;
	}

	cleanUp {
		modControl.unmap.removeDependant(this).remove;
		prOutBus.free;
		watcher.remove;
	}

	pollRate_ { |rate|
		pollRate = rate;
		if(synthInstance.notNil) {
			synthInstance.set(\pollRate, pollRate * (watcher.notNil.binaryValue));
		};
		modControl.do({ |ctl| ctl.pollRate = rate });
	}
}