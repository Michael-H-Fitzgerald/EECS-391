package edu.cwru.sepia.agent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Stack;

import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;

public class AstarAgent extends Agent {

	private static final long serialVersionUID = 1L;

	class MapLocation
	{
		public int x, y;

		public MapLocation(int x, int y, MapLocation cameFrom, float cost)
		{
			this.x = x;
			this.y = y;
		}
	}


	/**
	 * A wrapper for the MapLocation class primarily because I wanted to override the
	 * equals/hash code methods and make the constructor different and didn't want to 
	 * change the provided code too much. 
	 * 
	 * @author Sarah Whelan
	 *
	 */
	public class MapLocationWrapper {
		public int x;
		public int y;

		public MapLocationWrapper(MapLocation loc){
			this.x = loc.x;
			this.y = loc.y;
		}

		public MapLocationWrapper(int x, int y){
			this.x = x;
			this.y = y;
		}

		public MapLocation getMapLocation(){
			return new MapLocation(x, y, null, 0);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + x;
			result = prime * result + y;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MapLocationWrapper other = (MapLocationWrapper) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}
		private AstarAgent getOuterType() {
			return AstarAgent.this;
		}
	}

	/**
	 * A map location with associated cost estimate and a way to get the previous location.
	 * 
	 * @author Sarah Whelan
	 *
	 */
	public class SearchNode {
		public MapLocationWrapper location;
		public SearchNode cameFrom;
		public float cost;

		public SearchNode(MapLocationWrapper location, SearchNode cameFrom, float cost)
		{
			this.location = location;
			this.cameFrom = cameFrom;
			this.cost = cost;
		}
	}

	// The last known position of the enemyFootman
	private MapLocationWrapper previousEnemyLocation; 

	Stack<MapLocation> path;
	int footmanID, townhallID, enemyFootmanID;
	MapLocation nextLoc;

	private long totalPlanTime = 0; // nsecs
	private long totalExecutionTime = 0; //nsecs

	public AstarAgent(int playernum)
	{
		super(playernum);

		System.out.println("Constructed AstarAgent");
	}

	@Override
	public Map<Integer, Action> initialStep(State.StateView newstate, History.HistoryView statehistory) {
		// get the footman location
		List<Integer> unitIDs = newstate.getUnitIds(playernum);

		if(unitIDs.size() == 0)
		{
			System.err.println("No units found!");
			return null;
		}

		footmanID = unitIDs.get(0);

		// double check that this is a footman
		if(!newstate.getUnit(footmanID).getTemplateView().getName().equals("Footman"))
		{
			System.err.println("Footman unit not found");
			return null;
		}

		// find the enemy playernum
		Integer[] playerNums = newstate.getPlayerNumbers();
		int enemyPlayerNum = -1;
		for(Integer playerNum : playerNums)
		{
			if(playerNum != playernum) {
				enemyPlayerNum = playerNum;
				break;
			}
		}

		if(enemyPlayerNum == -1)
		{
			System.err.println("Failed to get enemy playernumber");
			return null;
		}

		// find the townhall ID
		List<Integer> enemyUnitIDs = newstate.getUnitIds(enemyPlayerNum);

		if(enemyUnitIDs.size() == 0)
		{
			System.err.println("Failed to find enemy units");
			return null;
		}

		townhallID = -1;
		enemyFootmanID = -1;
		for(Integer unitID : enemyUnitIDs)
		{
			Unit.UnitView tempUnit = newstate.getUnit(unitID);
			String unitType = tempUnit.getTemplateView().getName().toLowerCase();
			if(unitType.equals("townhall"))
			{
				townhallID = unitID;
			}
			else if(unitType.equals("footman"))
			{
				enemyFootmanID = unitID;
			}
			else
			{
				System.err.println("Unknown unit type");
			}
		}

		if(townhallID == -1) {
			System.err.println("Error: Couldn't find townhall");
			return null;
		}

		long startTime = System.nanoTime();
		path = findPath(newstate);
		totalPlanTime += System.nanoTime() - startTime;

		return middleStep(newstate, statehistory);
	}

	@Override
	public Map<Integer, Action> middleStep(State.StateView newstate, History.HistoryView statehistory) {
		long startTime = System.nanoTime();
		long planTime = 0;

		Map<Integer, Action> actions = new HashMap<Integer, Action>();

		if(shouldReplanPath(newstate, statehistory, path)) {
			long planStartTime = System.nanoTime();
			path = findPath(newstate);
			planTime = System.nanoTime() - planStartTime;
			totalPlanTime += planTime;
		}

		Unit.UnitView footmanUnit = newstate.getUnit(footmanID);

		int footmanX = footmanUnit.getXPosition();
		int footmanY = footmanUnit.getYPosition();

		if(!path.empty() && (nextLoc == null || (footmanX == nextLoc.x && footmanY == nextLoc.y))) {

			// stat moving to the next step in the path
			nextLoc = path.pop();

			System.out.println("Moving to (" + nextLoc.x + ", " + nextLoc.y + ")");
		}

		if(nextLoc != null && (footmanX != nextLoc.x || footmanY != nextLoc.y))
		{
			int xDiff = nextLoc.x - footmanX;
			int yDiff = nextLoc.y - footmanY;

			// figure out the direction the footman needs to move in
			Direction nextDirection = getNextDirection(xDiff, yDiff);

			actions.put(footmanID, Action.createPrimitiveMove(footmanID, nextDirection));
		} else {
			Unit.UnitView townhallUnit = newstate.getUnit(townhallID);

			// if townhall was destroyed on the last turn
			if(townhallUnit == null) {
				terminalStep(newstate, statehistory);
				return actions;
			}

			if(Math.abs(footmanX - townhallUnit.getXPosition()) > 1 ||
					Math.abs(footmanY - townhallUnit.getYPosition()) > 1)
			{
				System.err.println("Invalid plan. Cannot attack townhall");
				totalExecutionTime += System.nanoTime() - startTime - planTime;
				return actions;
			}
			else {
				System.out.println("Attacking TownHall");
				// if no more movements in the planned path then attack
				actions.put(footmanID, Action.createPrimitiveAttack(footmanID, townhallID));
			}
		}

		totalExecutionTime += System.nanoTime() - startTime - planTime;
		return actions;
	}

	@Override
	public void terminalStep(State.StateView newstate, History.HistoryView statehistory) {
		System.out.println("Total turns: " + newstate.getTurnNumber());
		System.out.println("Total planning time: " + totalPlanTime/1e9);
		System.out.println("Total execution time: " + totalExecutionTime/1e9);
		System.out.println("Total time: " + (totalExecutionTime + totalPlanTime)/1e9);
	}

	@Override
	public void savePlayerData(OutputStream os) {

	}

	@Override
	public void loadPlayerData(InputStream is) {

	}

	/**
	 * You will implement this method.
	 *
	 * This method should return true when the path needs to be replanned
	 * and false otherwise. This will be necessary on the dynamic map where the
	 * footman will move to block your unit.
	 *
	 * @param state
	 * @param history
	 * @param currentPath
	 * @return true if the path to the goal should be recalculated false if the currentPath should still work
	 */
	private boolean shouldReplanPath(State.StateView state, History.HistoryView history, Stack<MapLocation> currentPath){   
		// Don't re-plan the path if nothing could have changed since it was first planned ie no enemy
		Unit.UnitView enemy = state.getUnit(enemyFootmanID);
		if(enemy == null){
			return false;
		}

		boolean shouldReplan = false;
		MapLocationWrapper enemyLocation = new MapLocationWrapper(enemy.getXPosition(), enemy.getYPosition());        
		shouldReplan = hasEnemyMovementMattered(state, enemyLocation);
		previousEnemyLocation = enemyLocation;
		if(!shouldReplan){
			// Only check this if necessary we are not already re-planning and this might change our minds
			shouldReplan = isEnemyInNextFewSteps(currentPath, enemyLocation);
		}
		return shouldReplan;
	}

	/**
	 * By way of a class variable determines if the enemy has moved since the last time this method has been called.
	 * Additionally checks if we care about the movement of the enemy ie the enemy is closer to the goal than 
	 * our footman is and could still potentially block our path.
	 * 
	 * @param state
	 * @param currentEnemyLocation
	 * @return true if the enemy has moved and is closer to the goal than our footman
	 */
	private boolean hasEnemyMovementMattered(State.StateView state, MapLocationWrapper currentEnemyLocation){
		boolean enemyMovementMatters = false;
		Unit.UnitView footman = state.getUnit(footmanID);
		MapLocationWrapper footmanLocation = new MapLocationWrapper(footman.getXPosition(), footman.getYPosition());

		// If we know where the footman used to be
		if(previousEnemyLocation != null){
			// and it is not the current enemy location
			if(!previousEnemyLocation.equals(currentEnemyLocation)){
				Unit.UnitView townhall = state.getUnit(townhallID);
				MapLocation townhallLocation = new MapLocation(townhall.getXPosition(), townhall.getYPosition(), null, 0);
				// Determine if the enemy is closer to the goal than our footman
				if(heuristic(footmanLocation, townhallLocation) > 
				heuristic(currentEnemyLocation, townhallLocation)){
					// and if they are we care
					enemyMovementMatters = true;
				}
			}
		}
		return enemyMovementMatters;
	}

	/**
	 * Determines if the enemy is on the next few steps of the path
	 * 
	 * @param currentPath
	 * @param enemyLocation
	 * @return true if within a few steps the enemy is on the MapLocation false otherwise
	 */
	private boolean isEnemyInNextFewSteps(Stack<MapLocation> currentPath, MapLocationWrapper enemyLocation){
		boolean enemyOnPath = false;
		int NUM_STEPS_AHEAD_TO_CHECK = 4; // the number that determines "the few"

		// A stack to allow popping off the next few steps of the path 
		Stack<MapLocation> tempStack = new Stack<MapLocation>();
		for(int i = 0; i < NUM_STEPS_AHEAD_TO_CHECK; i++){
			if(!currentPath.isEmpty()){
				MapLocation stepInPath = currentPath.pop();
				tempStack.push(stepInPath);
				if(new MapLocationWrapper(stepInPath).equals(enemyLocation)){        		
					enemyOnPath = true;
				}
			}
		}

		// Put the steps that were popped in checking back onto the path
		while(!tempStack.isEmpty()){
			currentPath.push(tempStack.pop());
		}
		return enemyOnPath;
	}

	/**
	 * This method is implemented for you. You should look at it to see examples of
	 * how to find units and resources in Sepia.
	 *
	 * @param state
	 * @return
	 */
	private Stack<MapLocation> findPath(State.StateView state)
	{
		Unit.UnitView townhallUnit = state.getUnit(townhallID);
		Unit.UnitView footmanUnit = state.getUnit(footmanID);

		MapLocation startLoc = new MapLocation(footmanUnit.getXPosition(), footmanUnit.getYPosition(), null, 0);

		MapLocation goalLoc = new MapLocation(townhallUnit.getXPosition(), townhallUnit.getYPosition(), null, 0);

		MapLocation footmanLoc = null;
		if(enemyFootmanID != -1) {
			Unit.UnitView enemyFootmanUnit = state.getUnit(enemyFootmanID);
			footmanLoc = new MapLocation(enemyFootmanUnit.getXPosition(), enemyFootmanUnit.getYPosition(), null, 0);
		}

		// get resource locations
		List<Integer> resourceIDs = state.getAllResourceIds();
		Set<MapLocation> resourceLocations = new HashSet<MapLocation>();
		for(Integer resourceID : resourceIDs)
		{
			ResourceNode.ResourceView resource = state.getResourceNode(resourceID);

			resourceLocations.add(new MapLocation(resource.getXPosition(), resource.getYPosition(), null, 0));
		}

		return AstarSearch(startLoc, goalLoc, state.getXExtent(), state.getYExtent(), footmanLoc, resourceLocations);
	}
	/**
	 * This is the method you will implement for the assignment. Your implementation
	 * will use the A* algorithm to compute the optimum path from the start position to
	 * a position adjacent to the goal position.
	 *
	 * You will return a Stack of positions with the top of the stack being the first space to move to
	 * and the bottom of the stack being the last space to move to. If there is no path to the townhall
	 * then return null from the method and the agent will print a message and do nothing.
	 * The code to execute the plan is provided for you in the middleStep method.
	 *
	 * As an example consider the following simple map
	 *
	 * F - - - -
	 * x x x - x
	 * H - - - -
	 *
	 * F is the footman
	 * H is the townhall
	 * x's are occupied spaces
	 *
	 * xExtent would be 5 for this map with valid X coordinates in the range of [0, 4]
	 * x=0 is the left most column and x=4 is the right most column
	 *
	 * yExtent would be 3 for this map with valid Y coordinates in the range of [0, 2]
	 * y=0 is the top most row and y=2 is the bottom most row
	 *
	 * resourceLocations would be {(0,1), (1,1), (2,1), (4,1)}
	 *
	 * The path would be
	 *
	 * (1,0)
	 * (2,0)
	 * (3,1)
	 * (2,2)
	 * (1,2)
	 *
	 * Notice how the initial footman position and the townhall position are not included in the path stack
	 *
	 * @param start Starting position of the footman
	 * @param goal MapLocation of the townhall
	 * @param xExtent Width of the map
	 * @param yExtent Height of the map
	 * @param resourceLocations Set of positions occupied by resources
	 * @return Stack of positions with top of stack being first move in plan
	 */
	private Stack<MapLocation> AstarSearch(MapLocation start, MapLocation goal, int xExtent, int yExtent, MapLocation enemyFootmanLoc, Set<MapLocation> resourceLocations)
	{   
		// Queue of SearchNodes starting with lowest cost
		PriorityQueue<SearchNode> nodesToSearchBestFirst = 
				new PriorityQueue<SearchNode>(xExtent * yExtent, 
						// The comparator only takes cost estimate into account
						(SearchNodeA, SearchNodeB) -> {
							if(SearchNodeA.cost > SearchNodeB.cost){
								return 1;
							} else if (SearchNodeA.cost < SearchNodeB.cost){
								return -1;
							} else {
								return 0;
							}
						});
		// A fast way to see if a node is in the open list w/o iterating through the priority queue
		Set<MapLocationWrapper> locationsToSearch = new HashSet<MapLocationWrapper>();
		// A wrapper for the resources to enable calling .contains/.equals
		Set<MapLocationWrapper> resourceLocationsSet = new HashSet<MapLocationWrapper>();
		// Fill the wrapper with all of the resource locations
		resourceLocations.stream().forEach(e -> resourceLocationsSet.add(new MapLocationWrapper(e)));
		// A fast way to see if a node has already been expanded
		Set<MapLocationWrapper> seenNodes = new HashSet<MapLocationWrapper>();

		// Add the current/starting position to the openList
		SearchNode startNode = new SearchNode(new MapLocationWrapper(start), null, 0);
		nodesToSearchBestFirst.add(startNode);
		locationsToSearch.add(startNode.location);

		while(!nodesToSearchBestFirst.isEmpty()){ // Loop until we have seen all locations or found the goal 
			SearchNode current = nodesToSearchBestFirst.remove();
			if(current.location.x == goal.x && current.location.y == goal.y){
				return createPath(current); // Early termination of loop
			} else {
				epandNode(current, nodesToSearchBestFirst, locationsToSearch, resourceLocationsSet, goal, seenNodes, enemyFootmanLoc, xExtent, yExtent);
			}
		}

		// We looked through all of the reachable locations and didn't see the goal
		System.out.println("No available path.");
		System.exit(0);
		return null;
	}

	private void epandNode(SearchNode current, PriorityQueue<SearchNode> nodesToSearchBestFirst,
			Set<MapLocationWrapper> locationsToSearch, Set<MapLocationWrapper> resourceLocationsSet, MapLocation goal, 
			Set<MapLocationWrapper> seenNodes, MapLocation enemyFootmanLoc, int xExtent, int yExtent) {
		
		// add the current state to the closed lists
		seenNodes.add(current.location);

		// Go in all directions
		Direction[] directions = Direction.values();
		for(int i = 0; i < directions.length; i++){
			MapLocationWrapper expandedLocation = getLocationForDirection(directions[i], current);

			SearchNode expanedSearchNode = new SearchNode(expandedLocation, current, current.cost + 1 + heuristic(expandedLocation, goal));

			// If the possible next node being considered is within the map bounds
			boolean withinMapBounds = expandedLocation.y < yExtent && expandedLocation.x < xExtent && expandedLocation.y >= 0 && expandedLocation.x >= 0;
			if( withinMapBounds &&
					!seenNodes.contains(expandedLocation) &&
					!resourceLocationsSet.contains(expandedLocation) &&
					(enemyFootmanLoc == null || // No enemy or isn't an enemy location
					(expandedLocation.y != enemyFootmanLoc.y || expandedLocation.x != enemyFootmanLoc.x)
							)
					){

				if(locationsToSearch.contains(expandedLocation)){
					// This node is on the open list
					Iterator<SearchNode> iterator = nodesToSearchBestFirst.iterator();
					boolean done = false;
					while(!done && iterator.hasNext()){
						SearchNode nodeToTest = iterator.next();
						if(nodeToTest.location.x == expanedSearchNode.location.x && nodeToTest.location.y == expanedSearchNode.location.y){
							done = true;
							if(nodeToTest.cost > expanedSearchNode.cost){
								// This path is a better way to get to this location
								nodesToSearchBestFirst.remove(nodeToTest);
								nodesToSearchBestFirst.add(expanedSearchNode);
							}
						}
					}

				} else {
					// we have not seen this node and it is a space we could go to
					nodesToSearchBestFirst.add(expanedSearchNode);
					locationsToSearch.add(expanedSearchNode.location);
				}
			}
		}
	}

	private Stack<MapLocation> createPath(SearchNode current) {
		Stack<MapLocation> path = new Stack<MapLocation>();
		while(current.cameFrom != null){
			path.push(current.cameFrom.location.getMapLocation());
			current = current.cameFrom;
		}
		path.pop(); // Don't include the starting location
		return path;
	}

	private MapLocationWrapper getLocationForDirection(Direction direction, SearchNode current) {
		switch(direction){
		case NORTH:
			return new MapLocationWrapper(current.location.x, current.location.y - 1);
		case EAST:
			return new MapLocationWrapper(current.location.x + 1, current.location.y);
		case NORTHEAST:
			return new MapLocationWrapper(current.location.x + 1, current.location.y - 1);
		case NORTHWEST:
			return new MapLocationWrapper(current.location.x - 1, current.location.y - 1);
		case SOUTH:
			return new MapLocationWrapper(current.location.x, current.location.y + 1);
		case SOUTHEAST:
			return new MapLocationWrapper(current.location.x + 1, current.location.y + 1);
		case SOUTHWEST:
			return new MapLocationWrapper(current.location.x - 1, current.location.y + 1);
		default: // WEST
			return new MapLocationWrapper(current.location.x - 1, current.location.y);
		}
	}

	/**
	 * A way to estimate the cost from a given location the goal
	 * 
	 * @param node the node requiring an estimate
	 * @param goal the goal of the map
	 * @return an estimate of how many steps it would take to get from the node to the goal. 
	 * The estimate is both admissible (never overestimates) and consistent (satisfies the triangle inequality). 
	 */
	private int heuristic(MapLocationWrapper node, MapLocation goal){
		return Math.max(Math.abs(goal.x - node.x), Math.abs(goal.y - node.y)) - 1; 
		// -1 as we are not trying to get to the goal but rather adjacent to goal
	}

	/**
	 * Primitive actions take a direction (e.g. NORTH, NORTHEAST, etc)
	 * This converts the difference between the current position and the
	 * desired position to a direction.
	 *
	 * @param xDiff Integer equal to 1, 0 or -1
	 * @param yDiff Integer equal to 1, 0 or -1
	 * @return A Direction instance (e.g. SOUTHWEST) or null in the case of error
	 */
	private Direction getNextDirection(int xDiff, int yDiff) {

		// figure out the direction the footman needs to move in
		if(xDiff == 1 && yDiff == 1)
		{
			return Direction.SOUTHEAST;
		}
		else if(xDiff == 1 && yDiff == 0)
		{
			return Direction.EAST;
		}
		else if(xDiff == 1 && yDiff == -1)
		{
			return Direction.NORTHEAST;
		}
		else if(xDiff == 0 && yDiff == 1)
		{
			return Direction.SOUTH;
		}
		else if(xDiff == 0 && yDiff == -1)
		{
			return Direction.NORTH;
		}
		else if(xDiff == -1 && yDiff == 1)
		{
			return Direction.SOUTHWEST;
		}
		else if(xDiff == -1 && yDiff == 0)
		{
			return Direction.WEST;
		}
		else if(xDiff == -1 && yDiff == -1)
		{
			return Direction.NORTHWEST;
		}

		System.err.println("Invalid path. Could not determine direction");
		return null;
	}
}
