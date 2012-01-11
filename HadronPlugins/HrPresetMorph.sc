HrPresetMorph : HadronPlugin
{
	var <surfaceView, <presetList, curPresets, nPresetText, addButton, <mouseXY, <compositeBack,
	canvasItems, refreshRoutine, rotateCounter, rotFunc, <>menuList, surfaceExtent,
	senseDistance, senseCurve;
	var availParams, activeParams, paramView;
	// we have to be able to check params against the paramView's lists
	var availParamStrings, activeParamStrings;
	var mouseIsDown = false, isMapped = false;
	var numChan, lagSynth, lagBus, lagCallback, pollRate;
	var guiThruButton, guiPassThru = true;

	*initClass
	{
		this.addHadronPlugin;
	}

	*new
	{|argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|

		var numIns = 0;
		var numOuts = 0;
		var bounds;
		var name = "HrPresetMorph";

		if(argExtraArgs.isNil,
		{
			bounds = Rect(200, 200, 610, 555);
		},
		{
			if(argExtraArgs.size == 1,
			{
				bounds = Rect(200, 200, 160 + argExtraArgs[0].asInteger, 555);
			},
			{
				bounds = Rect(200, 200, 160 + argExtraArgs[0].asInteger, argExtraArgs[1].asInteger + 505);
			});
		});

		^super.new(argParentApp, name, argIdent, argUniqueID, argExtraArgs, bounds, numIns, numOuts, argCanvasXY).init;
	}

	init
	{

		outerWindow.acceptsMouseOver = true;
		curPresets = Dictionary.new;
		canvasItems = List.new;
		mouseIsDown = false;

		helpString = "Cmd (ctrl) + drag presets to surface. Right click to presets (on list and canvas) for options.";

		menuList = ListView(window, Rect(0, 0, 100, 40)).background_(Color.white).visible_(false);

		rotateCounter = -pi;

		rotFunc =
		{//calculates coords for rotating cursor line
			rotateCounter = (rotateCounter + 0.4).wrap(-pi, pi);
			[([-25, 25] * rotateCounter.sin), ([-25, 25] * (rotateCounter + (pi/2)).sin)];
		};

		refreshRoutine = Routine({ loop({ surfaceView.refresh; pollRate.reciprocal.wait; }); });

		compositeBack = CompositeView(window, Rect(160, 0, window.bounds.width - 165, window.bounds.height - 100));

		surfaceExtent = Point(window.bounds.width - 165, window.bounds.height - 100);
		mouseXY = surfaceExtent * 0.5;  // center

		surfaceView = UserView(compositeBack, Rect(0, 0, surfaceExtent.x, surfaceExtent.y))
		.focusColor_(Color.gray(alpha: 0))
		.background_(Color.white)
		.mouseOverAction_({|...args| mouseXY = (args[1]@args[2]); })
		.mouseDownAction_({ |view, x, y|
			mouseXY = Point(x, y);
			if(this.isRefreshing == false,  // this might change later
			{
				//"starting".postln;
				this.pollRate = 25;
				refreshRoutine.reset;
				refreshRoutine.play(AppClock);
			});
			mouseIsDown = true;
		})
		.mouseMoveAction_
		({|...args|
			mouseXY = (args[1]@args[2]);
		})
		.mouseUpAction_
		({
			if(this.isRefreshing,
			{
				//"stopping".postln;
				refreshRoutine.stop;
				mouseIsDown = false;
				rotateCounter = -pi;
				surfaceView.refresh;
			});
		})
		.canReceiveDragHandler_({ true; })
		.receiveDragHandler_
		({|...args|

			if(GUI.id == \swing) {
				mouseXY ?? { mouseXY = 10@10 }
			} {
				mouseXY = Point(args[1], args[2])
			};
			if(canvasItems.detect({|item| item.name == presetList.items[presetList.value] }).notNil,
			{
				parentApp.displayStatus("HrPresetMorph: Preset" + presetList.items[presetList.value].asString + "is already on surface.", -1);
			},
			{
				canvasItems.add(HrPresetMorphItemView.new(this, mouseXY, presetList.items[presetList.value]));
			});
		})
		.drawFunc_
		({
			var lineXs, lineYs;

			if(this.isRefreshing,
			{
				# lineXs, lineYs = rotFunc.value;
				Pen.color = Color.black;
				Pen.line(lineXs[0]@lineYs[0] + mouseXY, lineXs[1]@lineYs[1] + mouseXY);
				Pen.stroke;
			});

			this.calcNewParams(mouseXY);

		});

		senseDistance = surfaceView.bounds.leftTop.dist(surfaceView.bounds.rightBottom).asInteger;
		senseCurve = Env(
			#[0, 0.05, 0.1, 0.9, 1],
			#[3, 0.5, 0.85, 0.15].normalizeSum,
			#[0, 0, 0, -4]
		);

		nPresetText = TextField(window, Rect(10, 10, 100, 20))
		.action_({ addButton.valueAction_(1); });

		addButton = Button(window, Rect(115, 10, 40, 20)).states_([["Add"]])
		.action_({ this.addPreset; });

		presetList = ListView(window, Rect(10, 40, 145, window.bounds.height - 170))
		//.mouseUpAction_({|view|  })
		.mouseDownAction_
		({|...args|

			//args.postln;

			if(GUI.id == \swing,
			{
				args[4].switch( 1, { args[4] = 0; }, 3, { args[4] = 1; }); //button bindings
				args[3].switch( 0, { args[3] = 256; }, 131072, { args[3] = 131330; }, 524288, { args[3] = 524576; }); //keyboard bindings
			});

			if((args[5] == 2) and: { presetList.items.size > 0; }, { this.loadPreset(args[0].value); });
			if((args[4] == 1) and: { presetList.items.size > 0; },
			{
				menuList.remove;
				menuList = ListView(window, Rect(args[1]+args[0].bounds.left, args[2]+args[0].bounds.top, 100, 40)).background_(Color.gray(0.85))
				.items_(["Delete", "Cancel"])
				.mouseUpAction_
				({|menu|

					if(menu.value == 0, { this.removeSelectedPreset; });
					menuList.visible = false;
				});
			});
		})
		.keyDownAction_
		({|...args|

			if((args[3] == 127) and: { args[0].items.size > 0; }, //if del pressed and items not empty
			{
				this.removeSelectedPreset;
			});
		});

		guiThruButton = Button(window, Rect(10, presetList.bounds.bottom + 5, 145, 20))
		.states_([
			["Target GUI updates off", Color.black, Color.gray(0.9)],
			["Target GUI updates ON", Color.black, Color(0.7, 1.0, 0.7)]
		])
		.value_(guiPassThru.binaryValue)
		.action_({ |view|
			guiPassThru = view.value > 0;
		});

		availParams = List.new;
		activeParams = List.new;
		// availItems = List.new;

		paramView = HrListSelector(
			window,
			Rect(10, window.bounds.height - 95, window.bounds.width - 20, 90)
		)
		.action_({ |view|
			var i, didChange = false;
			// 4 cases from HrListSelector:
			// all avail --> active (avail is now empty)
			// 1 avail --> active
			// 1 active --> avail
			// all active --> avail (active is now empty)
			// I guess this is a bit inconvenient...
			case
			{ view.availItems.size == 0 } {
				activeParams = availParams;
				didChange = true;
			}
			{ view.activeItems.size == 0 } {
				this.unmapSomeParams(activeParams);
				activeParams = List.new;
				didChange = true;
			}
			// removed 1 from active
			{ view.activeItems.size == (activeParams.size - 1) } {
				i = activeParamStrings.detectIndex { |str, i|
					str != view.activeItems[i]
				};
				this.unmapSomeParams([activeParams[i]]);
				if(i.notNil) {
					activeParams.removeAt(i);
				};
				didChange = true;
			}
			// added 1 to active
			{ view.activeItems.size == (activeParams.size + 1) } {
				// find thing in view that's not in my itemstrings
				i = view.activeItems.detectIndex { |str, i|
					str != activeParamStrings[i]
				};
				i = availParamStrings.indexOfEqual(view.activeItems[i]);
				if(i.notNil) {
					activeParams = activeParams.add(availParams[i]);
				};
				didChange = true;
			};
			if(didChange) { this.makeSynth };
			this.prUpdateParamGui;
		});

		this.rebuildParamList;

		saveGets =
		[
			{ presetList.items; },
			{ curPresets; },
			{ canvasItems.collect({|item| [item.name, item.view.bounds, item.color] }); },
			{ activeParams.collect { |pair| [pair[0].uniqueID, pair[1]] } },
			{ availParams.collect { |pair| [pair[0].uniqueID, pair[1]] } },
			{ guiPassThru }
		];

		saveSets =
		[
			{|argg| presetList.items_(argg); },
			{|argg| curPresets = argg; },
			{|argg|
				argg.do({|item|
					canvasItems.add(HrPresetMorphItemView.new(this, item[1].left@item[1].top, item[0]).color_(item[2]));
				})
			},
			{ |argg|
				activeParams = argg.collect { |pair|
					[parentApp.pluginFromID(pair[0]), pair[1]]
				};
			},
			{ |argg|
				availParams = argg.collect { |pair|
					[parentApp.pluginFromID(pair[0]), pair[1]];
				};
				this.prUpdateParamGui;
				this.makeSynth;
			},
			{ |argg|
				guiPassThru = (argg == "true");
				guiThruButton.value = guiPassThru.binaryValue;
			}
		];

		modGets.put(\surfaceX, { mouseXY.x / surfaceView.bounds.width; });
		modGets.put(\surfaceY, { mouseXY.y / surfaceView.bounds.height; });
		modGets.put(\surfaceXY, {
			[
				mouseXY.x / surfaceView.bounds.width,
				mouseXY.y / surfaceView.bounds.height
			]
		});

		modSets.put(\surfaceX, { |argg|
			argg = argg * surfaceView.bounds.width;
			{
				this.calcNewParams(argg@mouseXY.y, true);
				surfaceView.refresh;
			}.defer;
		});
		modSets.put(\surfaceY, { |argg| argg = argg * surfaceView.bounds.height;
			{
				this.calcNewParams(mouseXY.x@argg, true);
				surfaceView.refresh;
			}.defer;
		});
		modSets.put(\surfaceXY, { |argg = #[0, 0]|
			mouseXY = Point(*argg) * surfaceExtent;
			{
				this.calcNewParams(mouseXY, true);
				surfaceView.refresh;
			}.defer
		});

		modMapSets.putAll(modSets);

		this.makeSynth;
	}

	calcNewParams
	{|argXY|

		var divisors, tempSum = 0, newValues = Array(activeParams.size);

		if((canvasItems.size > 0) and: { this.isRefreshing }, {
			divisors = canvasItems.collect { |canItem|
				var tempDist;
				tempDist = 1 - (canItem.view.bounds.origin.dist(argXY) / senseDistance);
				tempDist * senseCurve[tempDist]
			};

			activeParams.do { |pair, i|
				var plugID = pair[0].uniqueID;
				var plugParam = pair[1];
				var nextVal = 0;
				tempSum = 0;

				canvasItems.do
				({|inCItem, j|

					var farSavedVal = curPresets.at(inCItem.name).at(plugID); //hold the plug first, and see if it exists
					var tempDist = 1 - (inCItem.view.bounds.origin.dist(argXY) / senseDistance);

					if(farSavedVal.notNil,
						{
							farSavedVal = farSavedVal.at(plugParam);
							nextVal = nextVal +
							(farSavedVal * (tempDist * senseCurve[tempDist]));
							tempSum = tempSum + divisors[j];
						});
				});

				nextVal = nextVal / tempSum;
				newValues.add(nextVal);
			};
		});
		lagCallback.value(newValues);
	}

	addPreset
	{
		var tempPresetName, nameCounter = 1, tempPreset, tempPresetItem,
		addFunc, isReplacing = false;
		var tempWin;

		tempPresetName = nPresetText.string.asSymbol;

		addFunc =
		{

			if(isReplacing == false,
			{
				while({presetList.items.detect({|item| item == tempPresetName; }).notNil} ,
				{
					tempPresetName = (nPresetText.string + "(%)".format(nameCounter.asString)).asSymbol;
					nameCounter = nameCounter + 1;
				});

				//add only if not replacing
				presetList.items = presetList.items.add(tempPresetName);
			});


			presetList.refresh;

			tempPreset = Dictionary.new;

			parentApp.alivePlugs.do
			({|plugItem|

				if(plugItem.modGets.size != 0,
				{
					tempPresetItem = Dictionary.new;
					plugItem.modGets.keys.do
					({|keyItem|
						tempPresetItem.put(keyItem, plugItem.modGets.at(keyItem).value);
					});
					tempPreset.put(plugItem.uniqueID, tempPresetItem);

				});
			});

			curPresets.put(tempPresetName, tempPreset);
		};

		if(presetList.items.detect({|item| item == tempPresetName; }).notNil,
		{

			tempWin = Window("Replace preset?", Rect(400, 400, 190, 85), resizable: false);
			StaticText(tempWin, Rect(0, 15, 190, 20)).string_("Are you sure?").align_(\center);
			Button(tempWin, Rect(10, 50, 80, 20)).states_([["Replace"]]).action_({ isReplacing = true; addFunc.value; tempWin.close; });
			Button(tempWin, Rect(100, 50, 80, 20)).states_([["Create new"]]).action_({ isReplacing = false; addFunc.value; tempWin.close; }).focus(true);

			tempWin.front;

		}, { addFunc.value; });

	}

	loadPreset
	{|argIndex|

		var tempName = presetList.items[argIndex];

		curPresets.at(tempName).keys.do
		({|plugID|

			curPresets.at(tempName).at(plugID).keys.do
			({|param|

				parentApp.idPlugDict.at(plugID).modSets.at(param).value(curPresets.at(tempName).at(plugID).at(param));
			});
		});
	}

	removeSelectedPreset
	{
		var temp;
		curPresets.removeAt(presetList.value);
		temp = canvasItems.detectIndex({|item| item.name == presetList.items[presetList.value] });
		if(temp.notNil,
		{
			canvasItems[temp].removeSelf;
			canvasItems.removeAt(temp);
			surfaceView.refresh;
		});

		presetList.items.removeAt(presetList.value);
		presetList.items = presetList.items; //hack? to .refresh does nothing...

	}

	removeFromSurface
	{|argSurfaceItem|

		canvasItems.remove(argSurfaceItem);
		argSurfaceItem.removeSelf;
		surfaceView.refresh;
	}

	// activeParams should be set before calling this
	rebuildParamList {
		var tempParams;
		availParams = List.new;
		parentApp.alivePlugs.do { |plug|
			// you may not use a HrPresetMorph to drive itself
			if(plug !== this and: { (tempParams = this.prParamsFromPlug(plug)).notNil }) {
				availParams.add(tempParams);
			};
		};
		availParams = availParams.flatten(1);
		this.prUpdateParamGui;
	}

	prParamsFromPlug { |plug|
		if(plug !== this and: { plug.modGets.size > 0 }) {
			^[plug, plug.modGets.keys.asArray.sort].flop
		} { ^nil };
	}
	prUpdateParamGui {
		var updateFunc = {
			availParamStrings = availParams.collect { |parmpair|
				if(parmpair[0].ident != "unnamed",
					{ parmpair[0].ident },
					{ parmpair[0].name }
				) ++ ":" ++ parmpair[1]
			};
			activeParamStrings = activeParams.collect { |parmpair|
				if(parmpair[0].ident != "unnamed", { parmpair[0].ident }, { parmpair[0].name })
				++ ":" ++ parmpair[1]
			};
			paramView.setAllAndActiveItems(availParamStrings, activeParamStrings);
		};
		if(this.canCallOS) { updateFunc.value } {
			defer(updateFunc)
		};
	}

	notifyPlugAdd { |plug|
		if(availParams.every { |pair| pair[0] !== plug }) {
			availParams = availParams ++ this.prParamsFromPlug(plug);
			this.prUpdateParamGui;
		};
	}
	notifyIdentChanged {
		this.rebuildParamList;  // somebody's name changed
	}

	notifyPlugKill
	{|argPlugin|

		var tempID = argPlugin.uniqueID;

		curPresets.do
		({|preset|

			preset.removeAt(tempID);
		});
	}

	cleanUp {
		this.unmapSomeParams(activeParams);
		this.releaseSynth;
		lagBus.free;
	}

	updateBusConnections
	{
	}

	isRefreshing { ^mouseIsDown or: { isMapped } }

	mapModCtl { |paramName, ctlBus, modulatorPlug|
		isMapped = ctlBus != -1;
		if(isMapped) { this.pollRate = modulatorPlug.tryPerform(\pollRate) ? 12 };
		{ surfaceView.refresh }.defer;  // if unmapped, remove the spinning cursor
	}
	pollRate_ { |newRate(12)|
		pollRate = newRate;
		if(lagSynth.notNil) { lagSynth.set(\pollRate, pollRate) };
	}

	mapActiveParams { |ctlBus|
		ctlBus = ctlBus.index;
		activeParams.do { |pair, i|
			pair[0].mapModCtl(pair[1], ctlBus + i);
		}
	}

	// you want to do this only for params that are removed, not all of them
	unmapSomeParams { |paramList|
		paramList.do { |pair, i|
			pair[0].mapModCtl(pair[1], -1);
		}
	}

	releaseSynth { lagSynth.free }

	makeSynth {
		var oldbus, oldsynth;
		// just to save a method selector in the big table
		var passThruFunc = { |newValues|
			if(guiPassThru) {
				defer {
					activeParams.do { |pair, i|
						pair[0].modMapSets[pair[1]].value(newValues[i]);
					};
				};
			}
		};
		if(numChan != max(1, activeParams.size)) {
			numChan = max(1, activeParams.size);
			fork {
				SynthDef("PresetMorphLag" ++ uniqueID ++ numChan, {
					|out, pollRate = 1, t_trig|
					var input = In.kr(out, numChan),
					envArray = Env([0, 1], [pollRate.reciprocal], \lin).asArray;
					input = input.asArray.collect { |chan|
						envArray.put(0, chan).put(4, chan);
						EnvGen.kr(envArray, t_trig);
					};
					// input = Lag.kr(input, max(1, pollRate).reciprocal);
					ReplaceOut.kr(out, input);
				}).add;
				Server.default.sync;
				oldbus = lagBus;
				oldsynth = lagSynth;
				// this will happen next time new values are calculated
				lagCallback = { |newValues|
					lagBus = Bus.control(Server.default, numChan);
					Server.default.makeBundle(nil, {
						// no uninitialized data
						// if activeParams is empty, so is newValues, don't send
						if(newValues.size > 0) {
							lagBus.setn(newValues);
						};
						lagSynth = Synth("PresetMorphLag" ++ uniqueID ++ numChan,
							[out: lagBus, pollRate: pollRate, t_trig: 1],
							group
						);
						this.mapActiveParams(lagBus);
						oldsynth.free;
						passThruFunc.value(newValues);
					});
					oldbus.free;  // no osc msg here
					// normal behavior: set bus only
					lagCallback = { |newValues|
						Server.default.makeBundle(nil, {
							if(newValues.size > 0) {
								lagBus.setn(newValues);
							};
							lagSynth.set(\t_trig, 1);
							passThruFunc.value(newValues);
						});
					};
				};
			}
		};
	}
}

HrPresetMorphItemView
{

	var caller, <name, mouseXY, <view, <color, <menuList;

	*new
	{|argParent, argCoords, argName|

		^super.new.init(argParent, argCoords, argName);
	}

	init
	{|argParent, argCoords, argName|

		name = argName;
		color = Color.rand;
		caller = argParent;

		//menuList = ListView(argParent.compositeBack, Rect(0, 0, 100, 40)).background_(Color.gray(0.85)).visible_(false);

		view = UserView(argParent.compositeBack, Rect(argCoords.x, argCoords.y, 5 + (name.asString.size*6.5), 20))
		.focusColor_(Color.gray(alpha: 0))
		.background_(color)
		.mouseDownAction_
		({|...args|

			if(GUI.id == \swing,
			{
				args[4].switch( 1, { args[4] = 0; }, 3, { args[4] = 1; }); //button bindings
				args[3].switch( 0, { args[3] = 256; }, 131072, { args[3] = 131330; }, 524288, { args[3] = 524576; }); //keyboard bindings
			});

			mouseXY = args[0].bounds.origin + (args[1]@args[2]);

			args[5].switch
			(
				2, //if double clicked, load the preset
				{
					caller.loadPreset(caller.presetList.items.indexOf(name.asSymbol));
				}

			);

			args[4].switch
			(
				1, //if right clicked
				{
					argParent.menuList.remove;
					argParent.menuList = ListView(argParent.compositeBack, Rect(mouseXY.x - argParent.compositeBack.bounds.left, mouseXY.y - argParent.compositeBack.bounds.top, 150, 40)).background_(Color.gray(0.85))
					.items_(["Remove from surface", "Cancel"])
					.mouseUpAction_
					({|menu|

						if(menu.value == 0, { caller.removeFromSurface(this); });
						argParent.menuList.visible = false;
					});

				}
			);
		})
		.mouseMoveAction_
		({|...args|

			var tempXY = args[0].bounds.origin + (args[1]@args[2]);
			var delta = tempXY - mouseXY;
			mouseXY = tempXY;
			//args[0].bounds = args[0].bounds.postln.moveBy(delta.x, delta.y);
			args[0].bounds =
			Rect
			(
				(args[0].bounds.left + delta.x).clip(0, caller.surfaceView.bounds.width - args[0].bounds.width),
				(args[0].bounds.top + delta.y).clip(0, caller.surfaceView.bounds.height - 20),
				args[0].bounds.width,
				args[0].bounds.height
			);

		})
		.drawFunc_
		({|view|

			Pen.color = Color.black;
			Pen.font = Font(Font.defaultMonoFace, 10);
			Pen.stringAtPoint(name.asString, 3@3);
		});
	}

	color_
	{|argColor|

		color = argColor;
		view.background_(color);
	}

	removeSelf
	{
		view.remove;
	}
}