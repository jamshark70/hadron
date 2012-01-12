HadronModTargetControl
{
	var parentApp, parentPlug, <currentSelPlugin, <currentSelParam, myView,
	targetAppMenu, targetParamMenu, loadHolder;
	
	*new
	{|argParentView, argBounds, argParentApp, argParentPlug|
	
		^super.new.init(argParentView, argBounds, argParentApp, argParentPlug);
	}
	
	init
	{|argParentView, argBounds, argParentApp, argParentPlug|
	
		parentApp = argParentApp;
		parentPlug = argParentPlug;

		myView = CompositeView(argParentView, Rect(argBounds.left, argBounds.top, argBounds.width, 20));
		
		targetAppMenu = PopUpMenu(myView, Rect(0, 0, (argBounds.width/2)-5, 20))
		.action_
		({|menu|
			var oldplug = currentSelPlugin;
			var numChannels = parentPlug.tryPerform(\targetControlSize) ? 1;
			var tempItems;
			if(menu.value == 0,
			{
				currentSelPlugin = nil;
				currentSelParam = nil;
				targetParamMenu.items = ["Nothing."];
				targetParamMenu.value = 0;
			},
			{
				currentSelPlugin = parentApp.alivePlugs[menu.value - 1];
				currentSelParam = nil;
				tempItems = currentSelPlugin.modSets.select({ |func, key|
					try {
						max(1, func.def.prototypeFrame.asArray[0].size) == numChannels
					} { |err|
						if(err.isKindOf(Exception)) {
							"%:% has an invalid modSet for %: %\n"
							.format(
								currentSelPlugin,
								currentSelPlugin.ident,
								key, func
							).warn;
							err.reportError;
						};
						false  // reject this modSet if the func is not valid
					}
				}).keys.asArray;
				targetParamMenu.items = ["Nothing"] ++ tempItems;
				targetParamMenu.value = 0;
			});
			this.changed(\currentSelPlugin, currentSelPlugin, oldplug, currentSelParam);
		});
		
		this.refreshAppMenu;
		
		targetParamMenu = PopUpMenu(myView, Rect((argBounds.width/2)+5, 0, (argBounds.width/2)-5, 20))
		.items_(["Nothing."])
		.action_
		({|menu|
			var oldparam = currentSelParam;
			if(menu.value == 0,
			{
				currentSelParam = nil;
			},
			{
				currentSelParam = menu.item.asSymbol;
			});
			this.changed(\currentSelParam, currentSelParam, currentSelPlugin, oldparam);
		});
		
	}
	
	plugAdded
	{
		this.refreshAppMenu;
	}
	
	plugRemoved
	{|argPlug|
	
		// qt may supercalifragilistically clear targetAppMenu.value
		// before I get to finish up, so save the value now while I still can
		var plugIndex, appMenuValue = targetAppMenu.value;
		
		if(currentSelPlugin === argPlug,
		{
			this.refreshAppMenu(argPlug);
			currentSelPlugin = nil;
			currentSelParam = nil;
			targetParamMenu.items = ["Nothing."];
			targetParamMenu.value = 0;
			targetAppMenu.value = 0;
		},
		{
			plugIndex = parentApp.alivePlugs.indexOf(argPlug) + 1; //+1 because menu has an extra "Nothing" entry.
			if(appMenuValue < plugIndex,
			{
				this.refreshAppMenu(argPlug);
			},
			{
				this.refreshAppMenu(argPlug);
				targetAppMenu.value = appMenuValue - 1;
			});
		});
	}
	
	refreshAppMenu
	{|argRejectPlug|
		var oldval = max(0, targetAppMenu.value ? 0);
		targetAppMenu.items_(["Nothing."] ++ parentApp.alivePlugs.reject({|item| item === argRejectPlug; })
			.collect({|item| item.class.asString + item.ident }))
			.value_(oldval);
	}
	
	modulateWithValue
	{|argNormalizedValue|
	
		if((currentSelPlugin != nil) and: { currentSelParam != nil },
		{
			currentSelPlugin.modSets.at(currentSelParam.asSymbol).value(argNormalizedValue);
		});
	}

	updateMappedGui { |argRealValue|
		if((currentSelPlugin != nil) and: { currentSelParam != nil }) {
			currentSelPlugin.modMapSets.at(currentSelParam.asSymbol).value(argRealValue);
		};
	}

	map { |ctlBus|
		var mapped;
		if(mapped = (currentSelPlugin != nil) and: { currentSelParam != nil },
		{
			currentSelPlugin.mapModCtl(currentSelParam, ctlBus, parentPlug);
		});
		^mapped
	}
	unmap { |oldplug(currentSelPlugin), oldparam(currentSelParam)|
		if((oldplug != nil) and: { oldparam != nil },
		{
			oldplug.mapModCtl(oldparam, -1, parentPlug);
		});
	}
	
	getSaveValues
	{
		 ^[targetAppMenu.value, targetParamMenu.value];
	}
	
	putSaveValues
	{|argValArray|
	
		loadHolder = argValArray;
	}
	
	doWakeFromLoad
	{
		if(loadHolder.notNil) {
			targetAppMenu.valueAction_(loadHolder[0]);
			targetParamMenu.valueAction_(loadHolder[1]);
		}
	}

	remove {
		myView.remove;
		this.changed(\didRemove);
	}
}