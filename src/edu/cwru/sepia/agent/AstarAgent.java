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
    private MapLocationWrapper previousEnemyLoc; 
    
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
     * @return
     */
    private boolean shouldReplanPath(State.StateView state, History.HistoryView history, Stack<MapLocation> currentPath)
    {    
    	boolean result = false;
        Unit.UnitView enemyFootmanUnit = state.getUnit(enemyFootmanID);
        if(enemyFootmanUnit == null){
        	return result;
        }
        
        Unit.UnitView me = state.getUnit(footmanID);
        MapLocationWrapper meLoc = new MapLocationWrapper(me.getXPosition(), me.getYPosition());
        MapLocationWrapper footmanLoc = new MapLocationWrapper(enemyFootmanUnit.getXPosition(), enemyFootmanUnit.getYPosition());

        if(previousEnemyLoc != null){
        	if(!previousEnemyLoc.equals(footmanLoc)){
        		Unit.UnitView townhall = state.getUnit(townhallID);
        		MapLocation townhallLoc = new MapLocation(townhall.getXPosition(), townhall.getYPosition(), null, 0);
        		if(heuristic(meLoc, townhallLoc) > 
        		heuristic(footmanLoc, townhallLoc)){
        			result = true;
        		}
        	}
        }
        previousEnemyLoc = footmanLoc;        
                		
        int FORETHOUGHT = 4;
        Stack<MapLocation> otherStack = new Stack<MapLocation>();
        for(int i = 0; i < FORETHOUGHT; i++){
        	if(!currentPath.isEmpty()){
	        	MapLocation pathNode = currentPath.pop();
	        	otherStack.push(pathNode);
	        	if(pathNode.equals(footmanLoc)){        		
	        		result = true;
	        	}
        	}
        }
        while(!otherStack.isEmpty()){
        	currentPath.push(otherStack.pop());
        }
        
    	return result;
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
    	// The path to return
    	Stack<MapLocation> path = new Stack<MapLocation>();
    	// A fast way to get the search node with the lowest estimated cost to the goal
    	PriorityQueue<SearchNode> openList = 
    			// This queue will have at most the number of nodes on the map but it will almost always be less
    			// not actually sure what the lower/upper bounds are but needed to provide a default size in order
    			// to provide a comparator
    			new PriorityQueue<SearchNode>(xExtent * yExtent, 
    					// The comparator only takes cost into account
    					(o1, o2) -> {
    						if(o1.cost > o2.cost){
    							return 1;
    						} else if (o1.cost < o2.cost){
    							return -1;
    						} else {
    							return 0;
    						}
    					});
    	// A fast way to see if a node is in the open list w/o iterating through the priority queue
    	Set<MapLocationWrapper> openListSet = new HashSet<MapLocationWrapper>();
    	// A wrapper for the resources to enable calling .contains/.equals
    	Set<MapLocationWrapper> resources = new HashSet<MapLocationWrapper>();
    	// Fill the wrapper with all of the resource locations
    	resourceLocations.stream().forEach(e -> resources.add(new MapLocationWrapper(e)));
    	// A fast way to see if a node has already been expanded
    	Set<MapLocationWrapper> closedList = new HashSet<MapLocationWrapper>();
    	
    	// Start by adding the current/starting position to the openList
    	SearchNode startNode = new SearchNode(new MapLocationWrapper(start), null, 0);
    	openList.add(startNode);
    	openListSet.add(startNode.location);
    	
    	// As long as there are spaces to search
    	while(!openList.isEmpty()){
    		SearchNode current = openList.remove();
    		if(current.location == null){
    			continue;
    		}
    		// and we haven't found the goal
    		if(current.location.x == goal.x && current.location.y == goal.y){
    			SearchNode node = current;
    			while(node.cameFrom != null){
    				path.push(node.cameFrom.location.getMapLocation());
    				node = node.cameFrom;
    			}
    			path.pop();
    			return path;
    		} else {
    			// Expand a node from the open list by:
    			
    			// adding the current state to the closed list
    			closedList.add(current.location);
    			
    			// and check all the possible directions from the current node for possible moves
    			Direction[] directions = Direction.values();
    			for(int i = 0; i < directions.length; i++){
    				MapLocationWrapper location = null;
    				switch(directions[i]){
    				case NORTH:
    					location = new MapLocationWrapper(current.location.x, current.location.y - 1);
    					break;
					case EAST:
						location = new MapLocationWrapper(current.location.x + 1, current.location.y);
						break;
					case NORTHEAST:
						location = new MapLocationWrapper(current.location.x + 1, current.location.y - 1);
						break;
					case NORTHWEST:
						location = new MapLocationWrapper(current.location.x - 1, current.location.y - 1);
						break;
					case SOUTH:
						location = new MapLocationWrapper(current.location.x, current.location.y + 1);
						break;
					case SOUTHEAST:
						location = new MapLocationWrapper(current.location.x + 1, current.location.y + 1);
						break;
					case SOUTHWEST:
						location = new MapLocationWrapper(current.location.x - 1, current.location.y + 1);
						break;
					case WEST:
						location = new MapLocationWrapper(current.location.x - 1, current.location.y);
						break;
    				}
    				SearchNode next = new SearchNode(location, current, current.cost + 1 + heuristic(location, goal));
    				
    				// If the possible next node being considered is within the map bounds
					if(location.y < yExtent && location.x < xExtent && location.y >= 0 && location.x >= 0 &&
							// and has not been explored already
							!closedList.contains(location) &&
							// and is not a tree or something
							!resources.contains(location)){
						
						// If there isn't an enemy footman
						if(enemyFootmanLoc == null ||
								// or if there is and this spot isn't where they are
								(location.y != enemyFootmanLoc.y || location.x != enemyFootmanLoc.x)
								){
						
							// then see if it has already been seen by a different path
							if(openListSet.contains(location)){
								// if it has update the node in the priority queue
								Iterator<SearchNode> iter = openList.iterator();
								boolean done = false;
								while(!done && iter.hasNext()){
									SearchNode node = iter.next();
									if(node.location.x == next.location.x && node.location.y == next.location.y){
										done = true;
										if(node.cost > next.cost){
											openList.remove(node);
											openList.add(next);
										}
									}
								}
							} else {
								// otherwise this is the first time seeing the node and it is a valid (empty) space
								// add it to both the priority queue and open list
								openList.add(next);
								openListSet.add(next.location);
							}
						}
					}
    			}
    		}
    	}
    	
    	// We looked through all of the reachable states and didn't see the goal
    	System.out.println("No available path.");
    	System.exit(0);
        return null;
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
    	return Math.max(Math.abs(goal.x - node.x), Math.abs(goal.y - node.y));
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
