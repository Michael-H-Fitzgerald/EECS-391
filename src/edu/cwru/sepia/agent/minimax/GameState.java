package edu.cwru.sepia.agent.minimax;

import com.sun.org.apache.xpath.internal.operations.Bool;
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.action.ActionType;
import edu.cwru.sepia.action.DirectedAction;
import edu.cwru.sepia.action.TargetedAction;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.State;
import edu.cwru.sepia.environment.model.state.Unit;
import edu.cwru.sepia.util.Direction;
import edu.cwru.sepia.util.DistanceMetrics;
import org.omg.CORBA.ARG_IN;

import javax.xml.soap.Node;
import java.util.*;

/**
 * This class stores all of the information the agent needs to know about the
 * state of the game. For example this might include things like footmen HP and
 * positions.
 *
 * Add any information or methods you would like to this class, but do not
 * delete or change the signatures of the provided methods.
 */
public class GameState {
    public int numAttack = 0;
    public State.StateView savedState;
    public int yExtent;
    public int xExtent;
    public List<Integer> resourceIDs;
    public List<Integer> playerIDs;
    public List<Integer> enemyIDs;
    public int numPlayers;
    public int numArchers;
    public ArrayList<NodeState> units = new ArrayList<NodeState>();
    public ArrayList<MapLocation> resourceLocs = new ArrayList<MapLocation>();
    public double heuristic = 0;

    class MapLocation {
        public int x, y;
        public float cost;
        public MapLocation cameFrom;

        public MapLocation(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int hashCode() {
            return Arrays.hashCode(new Object[] { x, y });
        }

        public String toString() {
            return " {" + this.x + "," + this.y + "}" + " camefrom: " + cameFrom;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;

            MapLocation loc = (MapLocation) obj;
            if (obj != null && loc.x == this.x && loc.y == this.y)
                return true;
            else
                return false;
        }

    }

    public class NodeState {
        int xPos, yPos, unitHP, ID, range, basicAttack, type;

        public NodeState(int x, int y, int id, int unitHP, int basicAttack, int range, int type) {
            this.xPos = x;
            this.yPos = y;
            this.unitHP = unitHP;
            this.ID = id;
            this.range = range;
            this.basicAttack = basicAttack;
            this.type = type;
        }

        public NodeState(NodeState nodeState) {
            this.xPos = nodeState.xPos;
            this.yPos = nodeState.yPos;
            this.unitHP = nodeState.unitHP;
            this.ID = nodeState.ID;
            this.range = nodeState.range;
            this.basicAttack = nodeState.basicAttack;
            this.type = nodeState.type;
        }
    }

    /**
     * You will implement this constructor. It will extract all of the needed state
     * information from the built in SEPIA state view.
     * <p>
     * You may find the following state methods useful:
     * <p>
     * state.getXExtent() and state.getYExtent(): get the map dimensions
     * state.getAllResourceIDs(): returns the IDs of all of the obstacles in the map
     * state.getResourceNode(int resourceID): Return a ResourceView for the given ID
     * <p>
     * For a given ResourceView you can query the position using
     * resource.getXPosition() and resource.getYPosition()
     * <p>
     * You can get a list of all the units belonging to a player with the following
     * command: state.getUnitIds(int playerNum): gives a list of all unit IDs
     * beloning to the player. You control player 0, the enemy controls player 1.
     * <p>
     * In order to see information about a specific unit, you must first get the
     * UnitView corresponding to that unit. state.getUnit(int id): gives the
     * UnitView for a specific unit
     * <p>
     * With a UnitView you can find information about a given unit
     * unitView.getXPosition() and unitView.getYPosition(): get the current location
     * of this unit unitView.getHP(): get the current health of this unit
     * <p>
     * SEPIA stores information about unit types inside TemplateView objects. For a
     * given unit type you will need to find statistics from its Template View.
     * unitView.getTemplateView().getRange(): This gives you the attack range
     * unitView.getTemplateView().getBasicAttack(): The amount of damage this unit
     * type deals unitView.getTemplateView().getBaseHealth(): The initial amount of
     * health of this unit type
     *
     * @param state Current state of the episode
     */
    State.StateView state;

    public GameState(State.StateView state) {
        yExtent = state.getYExtent();
        savedState = state;
        xExtent = state.getXExtent();
        resourceIDs = state.getAllResourceIds();
        playerIDs = state.getUnitIds(0);
        enemyIDs = state.getUnitIds(1);
        numArchers = enemyIDs.size();
        numPlayers = playerIDs.size();
        this.state = state;
        int i = 0;
        // this will always fill up with 2 players to start and then 1 or 2 depending on
        // how many alive
        for (Integer unitID : playerIDs) {
            units.add(new NodeState(state.getUnit(playerIDs.get(i)).getXPosition(),
                    state.getUnit(playerIDs.get(i)).getYPosition(), state.getUnit(playerIDs.get(i)).getID(),
                    state.getUnit(playerIDs.get(i)).getHP(),
                    state.getUnit(playerIDs.get(i)).getTemplateView().getBasicAttack(),
                    state.getUnit(playerIDs.get(i)).getTemplateView().getRange(), 0 // 0 for player units
            ));
            i++;
        }
        i = 0;
        // this will either fill up with 1 or 2 archers
        for (Integer unitID : enemyIDs) {
            units.add( new NodeState(state.getUnit(enemyIDs.get(i)).getXPosition(),
                    state.getUnit(enemyIDs.get(i)).getYPosition(), state.getUnit(enemyIDs.get(i)).getID(),
                    state.getUnit(enemyIDs.get(i)).getHP(),
                    state.getUnit(enemyIDs.get(i)).getTemplateView().getBasicAttack(),
                    state.getUnit(enemyIDs.get(i)).getTemplateView().getRange(), 1) // 1 for archer units
            );
            i++;
        }
        for (Integer resourceID : resourceIDs) {
            ResourceNode.ResourceView resource = state.getResourceNode(resourceID);
            resourceLocs.add(new MapLocation(resource.getXPosition(), resource.getYPosition()));
        }
    }
    public GameState(GameState gameState) {
        yExtent = gameState.yExtent;
        savedState = gameState.state;
        xExtent = gameState.xExtent;
        resourceIDs = gameState.resourceIDs;
        playerIDs = gameState.playerIDs;
        enemyIDs = gameState.enemyIDs;
        numArchers = gameState.numArchers;
        numPlayers = gameState.numPlayers;
        units = gameState.units;
        resourceLocs = gameState.resourceLocs;
    }

    /**
     * You will implement this function.
     * <p>
     * You should use weighted linear combination of features. The features may be
     * primitives from the state (such as hp of a unit) or they may be higher level
     * summaries of information from the state such as distance to a specific
     * location. Come up with whatever features you think are useful and weight them
     * appropriately.
     * <p>
     * It is recommended that you start simple until you have your algorithm
     * working. Then watch your agent play and try to add features that correct
     * mistakes it makes. However, remember that your features should be as fast as
     * possible to compute. If the features are slow then you will be able to do
     * less plys in a turn.
     * <p>
     * Add a good comment about what is in your utility and why you chose those
     * features.
     *
     * @return The weighted linear combination of the features
     */
    public double getUtility(GameState a1) {
        double distanceUtil = 0;
        int numFootmen;
        if (a1.playerIDs.size() < 2) {
            numFootmen = 1;
        } else
            numFootmen = 2;

        //look through the footmen and archer, and calculate the min distance between archers and footmen
        //to include in the utility value
        double pythag1 = Integer.MAX_VALUE;
        double pythag2 = Integer.MAX_VALUE;
        double pythag3 = Integer.MAX_VALUE;
        double pythag4 = Integer.MAX_VALUE;

        // unit 1 and archer 1
        pythag1 = getDist2(a1.units.get(0).xPos, a1.units.get(0).yPos, a1.units.get(numFootmen).xPos, a1.units.get(numFootmen).yPos);
//        double changeX1 = Math.abs(a1.units.get(0).xPos - a1.units.get(0).xPos);
//        double changeY1 = Math.abs(a1.units.get(0).yPos - a1.units.get(0).yPos);
        //Math.sqrt(changeX1 * changeX1 + changeY1 * changeY1);

        if (a1.numArchers > 1) {
            // unit 1 and archer2
            pythag2 = getDist2(a1.units.get(0).xPos, a1.units.get(0).yPos, a1.units.get(1+numFootmen).xPos, a1.units.get(1+numFootmen).yPos);
//            double changeX2 = Math.abs(a1.units.get(0).xPos - a1.units.get(numFootmen + 1).xPos);
//            double changeY2 = Math.abs(a1.units.get(0).yPos - a1.units.get(numFootmen + 1).yPos);
//            pythag2 = Math.sqrt(changeX2 * changeX2 + changeY2 * changeY2);

        }
        if (numFootmen > 1) {
            // unit 2 and archer 1
            pythag3 = getDist2(a1.units.get(1).xPos, a1.units.get(1).yPos, a1.units.get(numFootmen).xPos, a1.units.get(numFootmen).yPos);
//            double changeX3 = Math.abs(a1.units.get(1).xPos - a1.units.get(numFootmen).xPos);
//            double changeY3 = Math.abs(a1.units.get(1).yPos - a1.units.get(numFootmen).yPos);
//            pythag3 = Math.sqrt(changeX3 * changeX3 + changeY3 * changeY3);

            if (a1.numArchers > 1) {
                // unit 2 and archer 2
                pythag4 = getDist2(a1.units.get(1).xPos, a1.units.get(1).yPos, a1.units.get(numFootmen+1).xPos, a1.units.get(1+numFootmen).yPos);
//                double changeX4 = Math.abs(a1.units.get(1).xPos - a1.units.get(numFootmen + 1).xPos);
//                double changeY4 = Math.abs(a1.units.get(1).yPos - a1.units.get(numFootmen + 1).yPos);
//                pythag4 = Math.sqrt(changeX4 * changeX4 + changeY4 * changeY4);

            }
        }
        // finds the min dist between players and archers

        if (numFootmen > 1) distanceUtil += Math.min(pythag3, pythag4) + Math.min(pythag1,pythag2);
        else distanceUtil = Math.min(pythag1, pythag2);

        //Now calculate the hpUtil
        int hpUtil = 0;
        for (int i = 0; i < numFootmen + a1.numArchers; i++) {
            if (a1.units.get(i).type == 0) {
                hpUtil += a1.units.get(i).unitHP;
            }
            else
                hpUtil -= a1.units.get(i).unitHP;
        }
        // find trees in area
        double resources = 0;
        // for each footman
        for (int j = 0; j < numFootmen; j++) {
            // for each space around him check resource locs
            for (MapLocation resource : resourceLocs) {
                if (a1.units.get(j).xPos+1 == resource.x && a1.units.get(j).yPos+1 == resource.y) resources++;
                if (a1.units.get(j).xPos+1 == resource.x && a1.units.get(j).yPos == resource.y) resources++;
                if (a1.units.get(j).xPos+1 == resource.x && a1.units.get(j).yPos-1 == resource.y) resources++;
                if (a1.units.get(j).xPos == resource.x && a1.units.get(j).yPos-1 == resource.y) resources++;
                if (a1.units.get(j).xPos-1 == resource.x && a1.units.get(j).yPos-1 == resource.y) resources++;
                if (a1.units.get(j).xPos-1 == resource.x && a1.units.get(j).yPos == resource.y) resources++;
                if (a1.units.get(j).xPos-1 == resource.x && a1.units.get(j).yPos+1 == resource.y) resources++;
                if (a1.units.get(j).xPos == resource.x && a1.units.get(j).yPos+1 == resource.y) resources++;
            }
        }
// make the total utility = hpUtil - a scalar of distanceUtil - scalar of resources nearby
        double addAttack = 0;
        if(addAttack == 1){
            addAttack = 100;
        }else if(addAttack == 2){
            addAttack = 100;
        }
        System.out.println(distanceUtil);
        return - (5*distanceUtil) - (5*resources) + addAttack;
    }



    int getDist2(int u1x, int u1y, int u2x, int u2y) {
        //System.out.println(DistanceMetrics.chebyshevDistance(u1x, u1y, u2x, u2y));
        return DistanceMetrics.chebyshevDistance(u1x, u1y, u2x, u2y);
    }




//    public double getUtility(GameState a1,boolean turn) {
//        int partner1;
//        int partner2;
//        int partner;
//        int[] closest1;
//        int[] closest2;
//        double distanceUtil;
//        // finds the min dist between players and archers
//        if (turn) {
//            partner1 = 0;
//            partner2 = 1;
//        } else if(!turn && a1.playerIDs.size() > 1){
//            partner1 = 2;
//            partner2 = 3;
//        }else{
//            partner1 = 1;
//            partner2 = 2;
//        }
//
//        if ((turn && playerIDs.size() > 1) || (!turn && enemyIDs.size() > 1)) {
//            NodeState agent1 = units.get(partner1);
//            NodeState agent2 = units.get(partner2);
//            if(turn) {
//                closest1 = getClosest(a1.state, agent1.ID, enemyIDs);
//                closest2 = getClosest(a1.state, agent2.ID, enemyIDs);
//                distanceUtil = closest1[1] + closest2[1];
//            }else{
//                closest1 = getClosest(a1.state, agent1.ID, playerIDs);
//                closest2 = getClosest(a1.state, agent2.ID, playerIDs);
//                distanceUtil = closest1[1] + closest2[1];
//            }
//        }else{
//            if (turn)
//                partner = 0;
//            else if (!turn && playerIDs.size()>1) {
//                partner = 2;
//            }else{
//                partner = 1;
//            }
//            if(turn){
//                closest1 = getClosest(a1.state, partner, enemyIDs);
//                distanceUtil = closest1[1];
//            }else{
//                closest1 = getClosest(a1.state, partner, playerIDs);
//                distanceUtil = closest1[1];
//            }
//        }
//
//
//        //Now calculate the hpUtil
//        int hpUtil = 0;
//        for (int i = 0; i < a1.playerIDs.size() + a1.numArchers; i++) {
//            if (a1.units.get(i).type == 0) {
//                hpUtil += a1.units.get(i).unitHP;
//            }
//            else
//                hpUtil -= a1.units.get(i).unitHP;
//        }
//        // find trees in area
//        double resources = 0;
//        // for each footman
//        for (int j = 0; j < a1.playerIDs.size() ; j++) {
//            // for each space around him check resource locs
//            for (MapLocation resource : resourceLocs) {
//                if (a1.units.get(j).xPos+1 == resource.x && a1.units.get(j).yPos+1 == resource.y) resources++;
//                if (a1.units.get(j).xPos+1 == resource.x && a1.units.get(j).yPos == resource.y) resources++;
//                if (a1.units.get(j).xPos+1 == resource.x && a1.units.get(j).yPos-1 == resource.y) resources++;
//                if (a1.units.get(j).xPos == resource.x && a1.units.get(j).yPos-1 == resource.y) resources++;
//                if (a1.units.get(j).xPos-1 == resource.x && a1.units.get(j).yPos-1 == resource.y) resources++;
//                if (a1.units.get(j).xPos-1 == resource.x && a1.units.get(j).yPos == resource.y) resources++;
//                if (a1.units.get(j).xPos-1 == resource.x && a1.units.get(j).yPos+1 == resource.y) resources++;
//                if (a1.units.get(j).xPos == resource.x && a1.units.get(j).yPos+1 == resource.y) resources++;
//            }
//        }
//// make the total utility = hpUtil - a scalar of distanceUtil - scalar of resources nearby
//        return hpUtil - (5*distanceUtil) - (5*resources);
//    }



    /**
     * You will implement this function.
     *
     * This will return a list of GameStateChild objects. You will generate all of the possible
     * actions in a step and then determine the resulting game state from that action. These are your GameStateChildren.
     *
     * It may be useful to be able to create a SEPIA Action. In this assignment you will
     * deal with movement and attacking actions. There are static methods inside the Action
     * class that allow you to create basic actions:
     * Action.createPrimitiveAttack(int attackerID, int targetID): returns an Action where
     * the attacker unit attacks the target unit.
     * Action.createPrimitiveMove(int unitID, Direction dir): returns an Action where the unit
     * moves one space in the specified direction.
     *
     * You may find it useful to iterate over all the different directions in SEPIA. This can
     * be done with the following loop:
     * for(Direction direction : Directions.values())
     *
     * To get the resulting position from a move in that direction you can do the following
     * x += direction.xComponent()
     * y += direction.yComponent()
     *
     * If you wish to explicitly use a Direction you can use the Direction enum, for example
     * Direction.NORTH or Direction.NORTHEAST.
     *
     * You can check many of the properties of an Action directly:
     * action.getType(): returns the ActionType of the action
     * action.getUnitID(): returns the ID of the unit performing the Action
     *
     * ActionType is an enum containing different types of actions. The methods given above
     * create actions of type ActionType.PRIMITIVEATTACK and ActionType.PRIMITIVEMOVE.
     *
     * For attack actions, you can check the unit that is being attacked. To do this, you
     * must cast the Action as a TargetedAction:
     * ((TargetedAction)action).getTargetID(): returns the ID of the unit being attacked
     *
     * @return All possible actions and their associated resulting game state
     */
    public boolean isEmpty(int x, int y, int ID) {
        ArrayList<MapLocation> unitLocations = new ArrayList<MapLocation>();
        List<Integer> otherPlayerIDs;
        if(playerIDs.contains(ID)){
            otherPlayerIDs = enemyIDs;
        }else{
            otherPlayerIDs = playerIDs;
        }
        for(NodeState i : units) {
            //Should only add the unit of the friendly player and check if beside
            if(i.ID != ID && !otherPlayerIDs.contains(i.ID)) unitLocations.add(new MapLocation(i.xPos,i.yPos));
        }
        MapLocation currentLocation = new MapLocation(x,y);
        if (!resourceLocs.contains(currentLocation) || !unitLocations.contains(currentLocation)) return true;
        else return false;
    }

    //These methods are taken from the archer class
    int[] getLoc(State.StateView state, int unitId) {
        Unit.UnitView unit = state.getUnit(unitId);
        if (unit == null) {
            return null;
        } else {
            int[] loc = new int[]{unit.getXPosition(), unit.getYPosition()};
            return loc;
        }
    }
    int getDist(int[] u1, int[] u2) {
        return DistanceMetrics.chebyshevDistance(u1[0], u1[1], u2[0], u2[1]);
    }

    int[] getClosest(State.StateView state, int unitId, List<Integer> opponentIds) {
        int[] unitLoc = this.getLoc(state, unitId);
        int closestOppoId = -1;
        int closestDist = 2147483647;

        for(int i = 0; i < opponentIds.size(); ++i) {
            int[] oppoLoc = this.getLoc(state, (Integer)opponentIds.get(i));
            int tempDist = this.getDist(unitLoc, oppoLoc);
            if (tempDist < closestDist) {
                closestDist = tempDist;
                closestOppoId = (Integer)opponentIds.get(i);
            }
        }

        return new int[]{closestOppoId, closestDist};
    }
    //def better way to do this
    public int checkForAttackNextToPlayer(int x, int y) {
        ArrayList<MapLocation> enemyUnitLocations = new ArrayList<MapLocation>();
        MapLocation currentLocation = new MapLocation(x,y);
        int myID = -1;
        for(NodeState i : units) {
            //Should only add the unit of the friendly player and check if beside
            if(i.xPos == x && i.yPos == y && i.type == 1){
                myID = i.ID;
            }
        }
        return myID;
    }

    public boolean legalMove(int x, int y, int comp1, int comp2) {
        return (!(Math.abs(comp1) == 1 && Math.abs(comp2) == 1) && x <= xExtent && x >= 0 && y <= yExtent && y >= 0);
    }


    public List<Action> getValidNeighbors(NodeState agent){
        ArrayList<Action> moves = new ArrayList<Action>();
        for(Direction direction: Direction.values()) {
            int nextX = agent.xPos + direction.xComponent();
            int nextY = agent.yPos + direction.yComponent();
            if(isEmpty(nextX, nextY, agent.ID) == true && legalMove(nextX, nextY,direction.xComponent(),direction.yComponent()) ) {
                if(checkForAttackNextToPlayer(nextX,nextY) == -1) {
                    moves.add(Action.createPrimitiveMove(agent.ID, direction));
                }else{
                    moves.add(Action.createPrimitiveAttack(agent.ID, checkForAttackNextToPlayer(nextX,nextY)));
                }
            }
        }
        return moves;
    }

    public List<GameStateChild> getChildren(Boolean turn) {
        List<Action> temp = new ArrayList<Action>();
        List<Action> temp2 = new ArrayList<Action>();
        List<GameStateChild> states = new ArrayList<GameStateChild>();
        int range = 10;
        int partner1;
        int partner2;
        int partner;

        if (turn) {
            partner1 = 0;
            partner2 = 1;
        } else if(!turn && playerIDs.size() > 1){
            partner1 = 2;
            partner2 = 3;
        }else{
            partner1 = 1;
            partner2 = 2;
        }

        if ((turn && playerIDs.size() > 1) || (!turn && enemyIDs.size() > 1)) {
            NodeState agent1 = units.get(partner1);
            NodeState agent2 = units.get(partner2);
            temp = getValidNeighbors(agent1);
            for (Action k : temp) {
                //archer attack1 moves
                temp2 = getValidNeighbors(agent2);
                for (Action j : temp2) {
                    HashMap<Integer, Action> allStates = new HashMap<Integer, Action>();
                    allStates.put(agent1.ID, k);
                    allStates.put(agent2.ID, j);
                    states.add(new GameStateChild(allStates, GameState.this));
                    //allStates.clear();
                }
            }
            if (!turn) {
                int[] archerOneDist;
                archerOneDist = getClosest(savedState, agent1.ID, playerIDs);
                if (archerOneDist[1] <= range) {
                    for (Action j : temp2) {
                        HashMap<Integer, Action> allStates1 = new HashMap<Integer, Action>();
                        Action AttackArcher = Action.createCompoundAttack(agent1.ID, archerOneDist[0]);
                        allStates1.put(agent1.ID, AttackArcher);
                        allStates1.put(agent2.ID, j);
                        states.add(new GameStateChild(allStates1, GameState.this));
                    }
                }
                int[] archerTwoDist;
                archerTwoDist = getClosest(savedState, agent2.ID, playerIDs);
                if (archerTwoDist[1] <= range) {
                    for (Action k : temp) {
                        HashMap<Integer, Action> allStates2 = new HashMap<Integer, Action>();
                        Action AttackArcher = Action.createCompoundAttack(agent2.ID, archerTwoDist[0]);
                        allStates2.put(agent2.ID, AttackArcher);
                        allStates2.put(agent1.ID, k);
                        states.add(new GameStateChild(allStates2, GameState.this));
                    }
                }
                if (archerTwoDist[1] <= range && archerOneDist[1] <= range) {
                    HashMap<Integer, Action> allStates3 = new HashMap<Integer, Action>();
                    Action AttackArcher1 = Action.createCompoundAttack(agent1.ID, archerTwoDist[0]);
                    Action AttackArcher2 = Action.createCompoundAttack(agent2.ID, archerTwoDist[0]);
                    allStates3.put(agent2.ID, AttackArcher1);
                    allStates3.put(agent1.ID, AttackArcher2);
                    states.add(new GameStateChild(allStates3, GameState.this));
                }
            }
        }
        //this is used for one player left
        else if (turn && playerIDs.size() > 0 || !turn && enemyIDs.size() > 0) {
            if (turn)
                partner = 0;
            else if (!turn && playerIDs.size()>1) {
                partner = 2;
            }else{
                partner = 1;
            }
            NodeState agent1 = units.get(partner);
            temp = getValidNeighbors(agent1);
            for (Action k : temp) {
                HashMap<Integer, Action> allStates = new HashMap<Integer, Action>();
                allStates.put(agent1.ID, k);
                states.add(new GameStateChild(allStates, GameState.this));
            }
            if (!turn) {
                int[] archerOneDist;
                archerOneDist = getClosest(savedState, agent1.ID, playerIDs);
                if (archerOneDist[1] <= range) {
                    for (Action j : temp2) {
                        HashMap<Integer, Action> allStates1 = new HashMap<Integer, Action>();
                        Action AttackArcher = Action.createCompoundAttack(agent1.ID, archerOneDist[0]);
                        allStates1.put(agent1.ID, AttackArcher);
                        states.add(new GameStateChild(allStates1, GameState.this));
                    }
                }
            }
        }
        return states;
    }
}
