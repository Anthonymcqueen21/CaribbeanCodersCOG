import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.io.*;
import java.util.*;
import java.awt.*;
class ObjectCloner
{
   // so that nobody can accidentally create an ObjectCloner object
   private ObjectCloner(){}
   // returns a deep copy of an object
   static public Object deepCopy(Object oldObj) throws Exception
   {
      ObjectOutputStream oos = null;
      ObjectInputStream ois = null;
      try
      {
         ByteArrayOutputStream bos = 
               new ByteArrayOutputStream(); // A
         oos = new ObjectOutputStream(bos); // B
         // serialize and pass the object
         oos.writeObject(oldObj);   // C
         oos.flush();               // D
         ByteArrayInputStream bin = 
               new ByteArrayInputStream(bos.toByteArray()); // E
         ois = new ObjectInputStream(bin);                  // F
         // return the new object
         return ois.readObject(); // G
      }
      catch(Exception e)
      {
         System.out.println("Exception in ObjectCloner = " + e);
         throw(e);
      }
      finally
      {
         oos.close();
         ois.close();
      }
   }
   
}

class LostException extends Exception implements Serializable {
    public LostException(String message) {
        super(message);
    }
}

class InvalidInputException extends Exception implements Serializable {
    public InvalidInputException(String message) {
        super(message);
    }
    public InvalidInputException(String message, String line) {
        super(message + line);
    }
}

class InvalidFormatException extends Exception implements Serializable {
    public InvalidFormatException(String message) {
        super(message);
    }
}

class GameOverException extends Exception implements Serializable {
    public GameOverException(String message) {
        super(message);
    }
}

class WinException extends Exception implements Serializable {
    public WinException(String message) {
        super(message);
    }
}

class Referee implements Serializable {
    private static final int LEAGUE_LEVEL = 3;

    private static final int MAP_WIDTH = 23;
    private static final int MAP_HEIGHT = 21;
    private static final int COOLDOWN_CANNON = 2;
    private static final int COOLDOWN_MINE = 5;
    private static final int INITIAL_SHIP_HEALTH = 100;
    private static final int MAX_SHIP_HEALTH = 100;
    private static final int MAX_SHIP_SPEED;
    private static final int MIN_SHIPS = 1;
    private static final int MAX_SHIPS;
    private static final int MIN_MINES;
    private static final int MAX_MINES;
    private static final int MIN_RUM_BARRELS = 10;
    private static final int MAX_RUM_BARRELS = 26;
    private static final int MIN_RUM_BARREL_VALUE = 10;
    private static final int MAX_RUM_BARREL_VALUE = 20;
    private static final int REWARD_RUM_BARREL_VALUE = 30;
    private static final int MINE_VISIBILITY_RANGE = 5;
    private static final int FIRE_DISTANCE_MAX = 10;
    private static final int LOW_DAMAGE = 25;
    private static final int HIGH_DAMAGE = 50;
    private static final int MINE_DAMAGE = 25;
    private static final int NEAR_MINE_DAMAGE = 10;
    private static final boolean CANNONS_ENABLED;
    private static final boolean MINES_ENABLED;

    static {
        switch (LEAGUE_LEVEL) {
        case 0: // 1 ship / no mines / speed 1
            MAX_SHIPS = 1;
            CANNONS_ENABLED = false;
            MINES_ENABLED = false;
            MIN_MINES = 0;
            MAX_MINES = 0;
            MAX_SHIP_SPEED = 1;
            break;
        case 1: // add mines
            MAX_SHIPS = 1;
            CANNONS_ENABLED = true;
            MINES_ENABLED = true;
            MIN_MINES = 5;
            MAX_MINES = 10;
            MAX_SHIP_SPEED = 1;
            break;
        case 2: // 3 ships max
            MAX_SHIPS = 3;
            CANNONS_ENABLED = true;
            MINES_ENABLED = true;
            MIN_MINES = 5;
            MAX_MINES = 10;
            MAX_SHIP_SPEED = 1;
            break;
        default: // increase max speed
            MAX_SHIPS = 3;
            CANNONS_ENABLED = true;
            MINES_ENABLED = true;
            MIN_MINES = 5;
            MAX_MINES = 10;
            MAX_SHIP_SPEED = 2;
            break;
        }
    }

    private static final Pattern PLAYER_INPUT_MOVE_PATTERN = Pattern.compile("MOVE (?<x>-?[0-9]{1,8})\\s+(?<y>-?[0-9]{1,8})(?:\\s+(?<message>.+))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_SLOWER_PATTERN = Pattern.compile("SLOWER(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_FASTER_PATTERN = Pattern.compile("FASTER(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_WAIT_PATTERN = Pattern.compile("WAIT(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_PORT_PATTERN = Pattern.compile("PORT(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_STARBOARD_PATTERN = Pattern.compile("STARBOARD(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYER_INPUT_FIRE_PATTERN = Pattern.compile("FIRE (?<x>-?[0-9]{1,8})\\s+(?<y>-?[0-9]{1,8})(?:\\s+(?<message>.+))?");
    private static final Pattern PLAYER_INPUT_MINE_PATTERN = Pattern.compile("MINE(?:\\s+(?<message>.+))?", Pattern.CASE_INSENSITIVE);

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    @SafeVarargs
    static final <T> String join(T... v) {
        return Stream.of(v).map(String::valueOf).collect(Collectors.joining(" "));
    }

    public static class Coord implements Serializable {
        private final static int[][] DIRECTIONS_EVEN = new int[][] { { 1, 0 }, { 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 }, { 0, 1 } };
        private final static int[][] DIRECTIONS_ODD = new int[][] { { 1, 0 }, { 1, -1 }, { 0, -1 }, { -1, 0 }, { 0, 1 }, { 1, 1 } };
        private final int x;
        private final int y;

        public Coord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public Coord(Coord other) {
            this.x = other.x;
            this.y = other.y;
        }

        public double angle(Coord targetPosition) {
            double dy = (targetPosition.y - this.y) * Math.sqrt(3) / 2;
            double dx = targetPosition.x - this.x + ((this.y - targetPosition.y) & 1) * 0.5;
            double angle = -Math.atan2(dy, dx) * 3 / Math.PI;
            if (angle < 0) {
                angle += 6;
            } else if (angle >= 6) {
                angle -= 6;
            }
            return angle;
        }

        CubeCoordinate toCubeCoordinate() {
            int xp = x - (y - (y & 1)) / 2;
            int zp = y;
            int yp = -(xp + zp);
            return new CubeCoordinate(xp, yp, zp);
        }

        Coord neighbor(int orientation) {
            int newY, newX;
            if (this.y % 2 == 1) {
                newY = this.y + DIRECTIONS_ODD[orientation][1];
                newX = this.x + DIRECTIONS_ODD[orientation][0];
            } else {
                newY = this.y + DIRECTIONS_EVEN[orientation][1];
                newX = this.x + DIRECTIONS_EVEN[orientation][0];
            }

            return new Coord(newX, newY);
        }

        boolean isInsideMap() {
            return x >= 0 && x < MAP_WIDTH && y >= 0 && y < MAP_HEIGHT;
        }

        int distanceTo(Coord dst) {
            return this.toCubeCoordinate().distanceTo(dst.toCubeCoordinate());
        }

    
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Coord other = (Coord) obj;
            return y == other.y && x == other.x;
        }

    
        public String toString() {
            return join(x, y);
        }
    }

    public static class CubeCoordinate implements Serializable {
        static int[][] directions = new int[][] { { 1, -1, 0 }, { +1, 0, -1 }, { 0, +1, -1 }, { -1, +1, 0 }, { -1, 0, +1 }, { 0, -1, +1 } };
        int x, y, z;

        public CubeCoordinate(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        Coord toOffsetCoordinate() {
            int newX = x + (z - (z & 1)) / 2;
            int newY = z;
            return new Coord(newX, newY);
        }

        CubeCoordinate neighbor(int orientation) {
            int nx = this.x + directions[orientation][0];
            int ny = this.y + directions[orientation][1];
            int nz = this.z + directions[orientation][2];

            return new CubeCoordinate(nx, ny, nz);
        }

        int distanceTo(CubeCoordinate dst) {
            return (Math.abs(x - dst.x) + Math.abs(y - dst.y) + Math.abs(z - dst.z)) / 2;
        }

    
        public String toString() {
            return join(x, y, z);
        }
    }

    private static enum EntityType {
        SHIP, BARREL, MINE, CANNONBALL
    }

    public static abstract class Entity implements Serializable {
        private static int UNIQUE_ENTITY_ID = 0;

        protected final int id;
        protected final EntityType type;
        protected Coord position;

        // WARNING ENTITYID MUST BE UNIQUE
        public Entity(int entityId, EntityType type, int x, int y) {
        	UNIQUE_ENTITY_ID = Math.max(UNIQUE_ENTITY_ID, entityId);
            this.id = entityId;
            this.type = type;
            this.position = new Coord(x, y);
        }
        
        public Entity(EntityType type, int x, int y) {
            this.id = UNIQUE_ENTITY_ID++;
            this.type = type;
            this.position = new Coord(x, y);
        }
      
		public int getId()
		{
			return this.id;
		}

        public String toViewString() {
            return join(id, position.y, position.x);
        }

        protected String toPlayerString(int arg1, int arg2, int arg3, int arg4) {
            return join(id, type.name(), position.x, position.y, arg1, arg2, arg3, arg4);
        }
    }

    public static class Mine extends Entity implements Serializable {
        public Mine(int x, int y) {
            super(EntityType.MINE, x, y);
        }
        
        public String toPlayerString(int playerIdx) {
            return toPlayerString(0, 0, 0, 0);
        }

        public List<Damage> explode(List<Ship> ships, boolean force) {
            List<Damage> damage = new ArrayList<>();
            Ship victim = null;

            for (Ship ship : ships) {
                if (position.equals(ship.bow()) || position.equals(ship.stern()) || position.equals(ship.position)) {
                    damage.add(new Damage(this.position, MINE_DAMAGE, true));
                    ship.damage(MINE_DAMAGE);
                    victim = ship;
                }
            }

            if (force || victim != null) {
                if (victim == null) {
                    damage.add(new Damage(this.position, MINE_DAMAGE, true));
                }

                for (Ship ship : ships) {
                    if (ship != victim) {
                        Coord impactPosition = null;
                        if (ship.stern().distanceTo(position) <= 1) {
                            impactPosition = ship.stern();
                        }
                        if (ship.bow().distanceTo(position) <= 1) {
                            impactPosition = ship.bow();
                        }
                        if (ship.position.distanceTo(position) <= 1) {
                            impactPosition = ship.position;
                        }

                        if (impactPosition != null) {
                            ship.damage(NEAR_MINE_DAMAGE);
                            damage.add(new Damage(impactPosition, NEAR_MINE_DAMAGE, true));
                        }
                    }
                }
            }

            return damage;
        }
    }

    public static class Cannonball extends Entity implements Serializable {
        final int ownerEntityId;
        final int srcX;
        final int srcY;
        final int initialRemainingTurns;
        int remainingTurns;

        public Cannonball(int row, int col, int ownerEntityId, int srcX, int srcY, int remainingTurns) {
            super(EntityType.CANNONBALL, row, col);
            this.ownerEntityId = ownerEntityId;
            this.srcX = srcX;
            this.srcY = srcY;
            this.initialRemainingTurns = this.remainingTurns = remainingTurns;
        }

        public String toViewString() {
            return join(id, position.y, position.x, srcY, srcX, initialRemainingTurns, remainingTurns, ownerEntityId);
        }

        public String toPlayerString(int playerIdx) {
            return toPlayerString(ownerEntityId, remainingTurns, 0, 0);
        }
    }

    public static class RumBarrel extends Entity implements Serializable {
        private int health;

        public RumBarrel(int x, int y, int health) {
            super(EntityType.BARREL, x, y);
            this.health = health;
        }

        public String toViewString() {
            return join(id, position.y, position.x, health);
        }

        public String toPlayerString(int playerIdx) {
            return toPlayerString(health, 0, 0, 0);
        }
    }

    public static class Damage implements Serializable {
        private final Coord position;
        private final int health;
        private final boolean hit;

        public Damage(Coord position, int health, boolean hit) {
            this.position = position;
            this.health = health;
            this.hit = hit;
        }
        
        public String toViewString() {
            return join(position.y, position.x, health, (hit ? 1 : 0));
        }
    }

    public static enum Action {
        FASTER, SLOWER, PORT, STARBOARD, FIRE, MINE
    }

    public static class Ship extends Entity implements Serializable {
        int orientation;
        int speed;
        int health;
        int initialHealth;
        int owner;
        String message;
        Action action;
        int mineCooldown;
        int cannonCooldown;
        Coord target;
        public int newOrientation;
        public Coord newPosition;
        public Coord newBowCoordinate;
        public Coord newSternCoordinate;
        
        // WARNING ENTITYID MUST BE UNIQUE (except for save)
        public Ship(int entityId, int x, int y, int orientation, int health, int owner) {
            super(entityId, EntityType.SHIP, x, y);
            this.orientation = orientation;
            this.speed = 0;
            this.health = health;
            this.owner = owner;
        }
        
        public Ship(int x, int y, int orientation, int owner) {
            super(EntityType.SHIP, x, y);
            this.orientation = orientation;
            this.speed = 0;
            this.health = INITIAL_SHIP_HEALTH;
            this.owner = owner;
        }
        
        public void update(int entityId, int x, int y, int orientation, int speed, int health, int owner)
        {
        	if (entityId == this.id)
        	{
                this.position = new Coord(x, y);
                this.orientation = orientation;
                this.speed = speed;
				this.health = health;
        	}
        }
        

        public Mine closestMine(List<Mine> mines) throws ExceptionInInitializerError
        {
            int minDistance = 99999;
            if (0 < mines.size())
            {
                Mine closestMine = null;
                for (Mine mine : mines)
                {
                    int distance = this.position.distanceTo(mine.position);
                    if (distance < minDistance) 
                    {
                        minDistance = distance;
                        closestMine = mine;
                    }
                }
                return closestMine;
            }
            else
            {
                throw new ExceptionInInitializerError("No barrel left");
            }
        }
        

        public RumBarrel closestBarrel(List<RumBarrel> barrels) throws ExceptionInInitializerError
        {
            int minDistance = 99999;
            if (0 < barrels.size())
            {
                RumBarrel closestBarrel = null;
                for (RumBarrel barrel : barrels)
                {
                    int distance = this.position.distanceTo(barrel.position) * barrel.health;
                    if (distance < minDistance) 
                    {
                        minDistance = distance;
                        closestBarrel = barrel;
                    }
                }
                return closestBarrel;
            }
            else
            {
                throw new ExceptionInInitializerError("No barrel left");
            }
        }

        public String toViewString() {
            return join(id, position.y, position.x, orientation, health, speed, (action != null ? action : "WAIT"), bow().y, bow().x, stern().y,
                    stern().x, " ;" + (message != null ? message : ""));
        }

        public String toPlayerString(int playerIdx) {
            return toPlayerString(orientation, speed, health, owner == playerIdx ? 1 : 0);
        }

        public void setMessage(String message) {
            if (message != null && message.length() > 50) {
                message = message.substring(0, 50) + "...";
            }
            this.message = message;
        }

        public void moveTo(int x, int y) {
            Coord currentPosition = this.position;
            Coord targetPosition = new Coord(x, y);

            if (currentPosition.equals(targetPosition)) {
                this.action = Action.SLOWER;
                return;
            }

            double targetAngle, angleStraight, anglePort, angleStarboard, centerAngle, anglePortCenter, angleStarboardCenter;

            switch (speed) {
            case 2:
                this.action = Action.SLOWER;
                break;
            case 1:
                // Suppose we've moved first
                currentPosition = currentPosition.neighbor(orientation);
                if (!currentPosition.isInsideMap()) {
                    this.action = Action.SLOWER;
                    break;
                }

                // Target reached at next turn
                if (currentPosition.equals(targetPosition)) {
                    this.action = null;
                    break;
                }

                // For each neighbor cell, find the closest to target
                targetAngle = currentPosition.angle(targetPosition);
                angleStraight = Math.min(Math.abs(orientation - targetAngle), 6 - Math.abs(orientation - targetAngle));
                anglePort = Math.min(Math.abs((orientation + 1) - targetAngle), Math.abs((orientation - 5) - targetAngle));
                angleStarboard = Math.min(Math.abs((orientation + 5) - targetAngle), Math.abs((orientation - 1) - targetAngle));

                centerAngle = currentPosition.angle(new Coord(MAP_WIDTH / 2, MAP_HEIGHT / 2));
                anglePortCenter = Math.min(Math.abs((orientation + 1) - centerAngle), Math.abs((orientation - 5) - centerAngle));
                angleStarboardCenter = Math.min(Math.abs((orientation + 5) - centerAngle), Math.abs((orientation - 1) - centerAngle));

                // Next to target with bad angle, slow down then rotate (avoid to turn around the target!)
                if (currentPosition.distanceTo(targetPosition) == 1 && angleStraight > 1.5) {
                    this.action = Action.SLOWER;
                    break;
                }

                Integer distanceMin = null;

                // Test forward
                Coord nextPosition = currentPosition.neighbor(orientation);
                if (nextPosition.isInsideMap()) {
                    distanceMin = nextPosition.distanceTo(targetPosition);
                    this.action = null;
                }

                // Test port
                nextPosition = currentPosition.neighbor((orientation + 1) % 6);
                if (nextPosition.isInsideMap()) {
                    int distance = nextPosition.distanceTo(targetPosition);
                    if (distanceMin == null || distance < distanceMin || distance == distanceMin && anglePort < angleStraight - 0.5) {
                        distanceMin = distance;
                        this.action = Action.PORT;
                    }
                }

                // Test starboard
                nextPosition = currentPosition.neighbor((orientation + 5) % 6);
                if (nextPosition.isInsideMap()) {
                    int distance = nextPosition.distanceTo(targetPosition);
                    if (distanceMin == null || distance < distanceMin
                            || (distance == distanceMin && angleStarboard < anglePort - 0.5 && this.action == Action.PORT)
                            || (distance == distanceMin && angleStarboard < angleStraight - 0.5 && this.action == null)
                            || (distance == distanceMin && this.action == Action.PORT && angleStarboard == anglePort
                                    && angleStarboardCenter < anglePortCenter)
                            || (distance == distanceMin && this.action == Action.PORT && angleStarboard == anglePort
                                    && angleStarboardCenter == anglePortCenter && (orientation == 1 || orientation == 4))) {
                        distanceMin = distance;
                        this.action = Action.STARBOARD;
                    }
                }
                break;
            case 0:
                // Rotate ship towards target
                targetAngle = currentPosition.angle(targetPosition);
                angleStraight = Math.min(Math.abs(orientation - targetAngle), 6 - Math.abs(orientation - targetAngle));
                anglePort = Math.min(Math.abs((orientation + 1) - targetAngle), Math.abs((orientation - 5) - targetAngle));
                angleStarboard = Math.min(Math.abs((orientation + 5) - targetAngle), Math.abs((orientation - 1) - targetAngle));

                centerAngle = currentPosition.angle(new Coord(MAP_WIDTH / 2, MAP_HEIGHT / 2));
                anglePortCenter = Math.min(Math.abs((orientation + 1) - centerAngle), Math.abs((orientation - 5) - centerAngle));
                angleStarboardCenter = Math.min(Math.abs((orientation + 5) - centerAngle), Math.abs((orientation - 1) - centerAngle));

                Coord forwardPosition = currentPosition.neighbor(orientation);

                this.action = null;

                if (anglePort <= angleStarboard) {
                    this.action = Action.PORT;
                }

                if (angleStarboard < anglePort || angleStarboard == anglePort && angleStarboardCenter < anglePortCenter
                        || angleStarboard == anglePort && angleStarboardCenter == anglePortCenter && (orientation == 1 || orientation == 4)) {
                    this.action = Action.STARBOARD;
                }

                if (forwardPosition.isInsideMap() && angleStraight <= anglePort && angleStraight <= angleStarboard) {
                    this.action = Action.FASTER;
                }
                break;
            }

        }

        public void faster() {
            this.action = Action.FASTER;
        }

        public void slower() {
            this.action = Action.SLOWER;
        }

        public void port() {
            this.action = Action.PORT;
        }

        public void starboard() {
            this.action = Action.STARBOARD;
        }

        public void placeMine() {
            if (MINES_ENABLED) {
                this.action = Action.MINE;
            }
        }

        public Coord stern() {
            return position.neighbor((orientation + 3) % 6);
        }

        public Coord bow() {
            return position.neighbor(orientation);
        }

        public Coord newStern() {
            return position.neighbor((newOrientation + 3) % 6);
        }

        public Coord newBow() {
            return position.neighbor(newOrientation);
        }

        public boolean at(Coord coord) {
            Coord stern = stern();
            Coord bow = bow();
            return stern != null && stern.equals(coord) || bow != null && bow.equals(coord) || position.equals(coord);
        }

        public boolean newBowIntersect(Ship other) {
            return newBowCoordinate != null && (newBowCoordinate.equals(other.newBowCoordinate) || newBowCoordinate.equals(other.newPosition)
                    || newBowCoordinate.equals(other.newSternCoordinate));
        }

        public boolean newBowIntersect(List<Ship> ships) {
            for (Ship other : ships) {
                if (this != other && newBowIntersect(other)) {
                    return true;
                }
            }
            return false;
        }

        public boolean newPositionsIntersect(Ship other) {
            boolean sternCollision = newSternCoordinate != null && (newSternCoordinate.equals(other.newBowCoordinate)
                    || newSternCoordinate.equals(other.newPosition) || newSternCoordinate.equals(other.newSternCoordinate));
            boolean centerCollision = newPosition != null && (newPosition.equals(other.newBowCoordinate) || newPosition.equals(other.newPosition)
                    || newPosition.equals(other.newSternCoordinate));
            return newBowIntersect(other) || sternCollision || centerCollision;
        }

        public boolean newPositionsIntersect(List<Ship> ships) {
            for (Ship other : ships) {
                if (this != other && newPositionsIntersect(other)) {
                    return true;
                }
            }
            return false;
        }

        public void damage(int health) {
            this.health -= health;
            if (this.health <= 0) {
                this.health = 0;
            }
        }

        public void heal(int health) {
            this.health += health;
            if (this.health > MAX_SHIP_HEALTH) {
                this.health = MAX_SHIP_HEALTH;
            }
        }

        public void fire(int x, int y) {
            if (CANNONS_ENABLED) {
                Coord target = new Coord(x, y);
                this.target = target;
                this.action = Action.FIRE;
            }
        }
    }

    private static class Player implements Serializable {
        private int id;
        private List<Ship> ships;
        private List<Ship> shipsAlive;

        public Player(int id) {
            this.id = id;
            this.ships = new ArrayList<Ship>();
            this.shipsAlive = new ArrayList<Ship>();
        }
        
        public void setDead() {
            for (Ship ship : ships) {
                ship.health = 0;
            }
        }

        public int getScore() {
            int score = 0;
            for (Ship ship : ships) {
                score += ship.health;
            }
            return score;
        }

        public List<String> toViewString() {
            List<String> data = new ArrayList<>();

            data.add(String.valueOf(this.id));
            for (Ship ship : ships) {
                data.add(ship.toViewString());
            }

            return data;
        }
    }
    
    protected int getEvaluation(int playerIdx, int opponentPlayerIdx) {
    	int distance = 0;
    	if (this.barrels.size() != 0)
    	{
        	for (Ship ship : this.players.get(playerIdx).shipsAlive)
        	{
        		distance +=ship.bow().distanceTo((ship.closestBarrel(this.barrels).position));
        	}
    	}

    	return distance + players.get(playerIdx).getScore() - players.get(opponentPlayerIdx).getScore();
    }

    private long seed;
    private List<Cannonball> cannonballs;
    private List<Mine> mines;
    private List<RumBarrel> barrels;
    private List<Player> players;
    private List<Ship> ships;
    private List<Damage> damage;
    private List<Ship> shipLosts;
    private List<Coord> cannonBallExplosions;
    private int shipsPerPlayer;
    private int mineCount;
    private int barrelCount;
    private Random random;

    public Referee(InputStream is, PrintStream out, PrintStream err) throws IOException {
        // super(is, out, err);
    }

    public static void main(String... args) throws IOException {
        new Referee(System.in, System.out, System.err);
    }

    protected void initEmptyReferee(int playerCount, Properties prop) throws InvalidFormatException
    {
        seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
        random = new Random(this.seed);

        shipsPerPlayer = clamp(
                Integer.valueOf(prop.getProperty("shipsPerPlayer", String.valueOf(random.nextInt(1 + MAX_SHIPS - MIN_SHIPS) + MIN_SHIPS))), MIN_SHIPS,
                MAX_SHIPS);

        if (MAX_MINES > MIN_MINES) {
            mineCount = clamp(Integer.valueOf(prop.getProperty("mineCount", String.valueOf(random.nextInt(MAX_MINES - MIN_MINES) + MIN_MINES))),
                    MIN_MINES, MAX_MINES);
        } else {
            mineCount = MIN_MINES;
        }

        barrelCount = clamp(
                Integer.valueOf(prop.getProperty("barrelCount", String.valueOf(random.nextInt(MAX_RUM_BARRELS - MIN_RUM_BARRELS) + MIN_RUM_BARRELS))),
                MIN_RUM_BARRELS, MAX_RUM_BARRELS);

        this.cannonballs = new ArrayList<>();
        this.cannonBallExplosions = new ArrayList<>();
        this.damage = new ArrayList<>();
        
        // Generate Players
        this.players = new ArrayList<Player>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            this.players.add(new Player(i));
        }

        this.ships = new ArrayList<>();
        this.mines = new ArrayList<>();
        this.barrels = new ArrayList<>();
    }
    

    protected void initReferee(int playerCount, Properties prop) throws InvalidFormatException {
        seed = Long.valueOf(prop.getProperty("seed", String.valueOf(new Random(System.currentTimeMillis()).nextLong())));
        random = new Random(this.seed);

        shipsPerPlayer = clamp(
                Integer.valueOf(prop.getProperty("shipsPerPlayer", String.valueOf(random.nextInt(1 + MAX_SHIPS - MIN_SHIPS) + MIN_SHIPS))), MIN_SHIPS,
                MAX_SHIPS);

        if (MAX_MINES > MIN_MINES) {
            mineCount = clamp(Integer.valueOf(prop.getProperty("mineCount", String.valueOf(random.nextInt(MAX_MINES - MIN_MINES) + MIN_MINES))),
                    MIN_MINES, MAX_MINES);
        } else {
            mineCount = MIN_MINES;
        }

        barrelCount = clamp(
                Integer.valueOf(prop.getProperty("barrelCount", String.valueOf(random.nextInt(MAX_RUM_BARRELS - MIN_RUM_BARRELS) + MIN_RUM_BARRELS))),
                MIN_RUM_BARRELS, MAX_RUM_BARRELS);

        cannonballs = new ArrayList<>();
        cannonBallExplosions = new ArrayList<>();
        damage = new ArrayList<>();

        // Generate Players
        this.players = new ArrayList<Player>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            this.players.add(new Player(i));
        }
        // Generate Ships
        for (int j = 0; j < shipsPerPlayer; j++) {
            int xMin = 1 + j * MAP_WIDTH / shipsPerPlayer;
            int xMax = (j + 1) * MAP_WIDTH / shipsPerPlayer - 2;

            int y = 1 + random.nextInt(MAP_HEIGHT / 2 - 2);
            int x = xMin + random.nextInt(1 + xMax - xMin);
            int orientation = random.nextInt(6);

            Ship ship0 = new Ship(x, y, orientation, 0);
            Ship ship1 = new Ship(x, MAP_HEIGHT - 1 - y, (6 - orientation) % 6, 1);

            this.players.get(0).ships.add(ship0);
            this.players.get(1).ships.add(ship1);
            this.players.get(0).shipsAlive.add(ship0);
            this.players.get(1).shipsAlive.add(ship1);
        }

        this.ships = players.stream().map(p -> p.ships).flatMap(List::stream).collect(Collectors.toList());

        // Generate mines
        mines = new ArrayList<>();
        while (mines.size() < mineCount) {
            int x = 1 + random.nextInt(MAP_WIDTH - 2);
            int y = 1 + random.nextInt(MAP_HEIGHT / 2);

            Mine m = new Mine(x, y);

            boolean cellIsFreeOfMines = mines.stream().noneMatch(mine -> mine.position.equals(m.position));
            boolean cellIsFreeOfShips = ships.stream().noneMatch(ship -> ship.at(m.position));

            if (cellIsFreeOfShips && cellIsFreeOfMines) {
                if (y != MAP_HEIGHT - 1 - y) {
                    mines.add(new Mine(x, MAP_HEIGHT - 1 - y));
                }
                mines.add(m);
            }
        }
        mineCount = mines.size();

        // Generate supplies
        barrels = new ArrayList<>();
        while (barrels.size() < barrelCount) {
            int x = 1 + random.nextInt(MAP_WIDTH - 2);
            int y = 1 + random.nextInt(MAP_HEIGHT / 2);
            int h = MIN_RUM_BARREL_VALUE + random.nextInt(1 + MAX_RUM_BARREL_VALUE - MIN_RUM_BARREL_VALUE);

            RumBarrel m = new RumBarrel(x, y, h);

            boolean cellIsFreeOfBarrels = barrels.stream().noneMatch(barrel -> barrel.position.equals(m.position));
            boolean cellIsFreeOfMines = mines.stream().noneMatch(mine -> mine.position.equals(m.position));
            boolean cellIsFreeOfShips = ships.stream().noneMatch(ship -> ship.at(m.position));

            if (cellIsFreeOfShips && cellIsFreeOfMines && cellIsFreeOfBarrels) {
                if (y != MAP_HEIGHT - 1 - y) {
                    barrels.add(new RumBarrel(x, MAP_HEIGHT - 1 - y, h));
                }
                barrels.add(m);
            }
        }
        barrelCount = barrels.size();
        
        this.ships = new ArrayList<>();
        this.mines = new ArrayList<>();
        this.barrels = new ArrayList<>();
        this.damage = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            this.players.add(new Player(i));
        }
    }

    protected Properties getConfiguration() {
        Properties prop = new Properties();
        prop.setProperty("seed", String.valueOf(seed));
        prop.setProperty("shipsPerPlayer", String.valueOf(shipsPerPlayer));
        prop.setProperty("barrelCount", String.valueOf(barrelCount));
        prop.setProperty("mineCount", String.valueOf(mineCount));
        return prop;
    }


    protected void prepare(int round) {
        for (Player player : players) {
            for (Ship ship : player.ships) {
                ship.action = null;
				ship.message = null;
            }
        }
        cannonBallExplosions.clear();
        damage.clear();
    }


    protected int getExpectedOutputLineCountForPlayer(int playerIdx) {
        return this.players.get(playerIdx).shipsAlive.size();
    }


    protected void handlePlayerOutput(int frame, int round, int playerIdx, String[] outputs)
            throws WinException, LostException, InvalidInputException {
        Player player = this.players.get(playerIdx);

        try {
            int i = 0;
            for (String line : outputs) {
                Matcher matchWait = PLAYER_INPUT_WAIT_PATTERN.matcher(line);
                Matcher matchMove = PLAYER_INPUT_MOVE_PATTERN.matcher(line);
                Matcher matchFaster = PLAYER_INPUT_FASTER_PATTERN.matcher(line);
                Matcher matchSlower = PLAYER_INPUT_SLOWER_PATTERN.matcher(line);
                Matcher matchPort = PLAYER_INPUT_PORT_PATTERN.matcher(line);
                Matcher matchStarboard = PLAYER_INPUT_STARBOARD_PATTERN.matcher(line);
                Matcher matchFire = PLAYER_INPUT_FIRE_PATTERN.matcher(line);
                Matcher matchMine = PLAYER_INPUT_MINE_PATTERN.matcher(line);
                Ship ship = player.shipsAlive.get(i++);

                if (matchMove.matches()) {
                    int x = Integer.parseInt(matchMove.group("x"));
                    int y = Integer.parseInt(matchMove.group("y"));
                    ship.setMessage(matchMove.group("message"));
                    ship.moveTo(x, y);
                } else if (matchFaster.matches()) {
                    ship.setMessage(matchFaster.group("message"));
                    ship.faster();
                } else if (matchSlower.matches()) {
                    ship.setMessage(matchSlower.group("message"));
                    ship.slower();
                } else if (matchPort.matches()) {
                    ship.setMessage(matchPort.group("message"));
                    ship.port();
                } else if (matchStarboard.matches()) {
                    ship.setMessage(matchStarboard.group("message"));
                    ship.starboard();
                } else if (matchWait.matches()) {
                    ship.setMessage(matchWait.group("message"));
                } else if (matchMine.matches()) {
                    ship.setMessage(matchMine.group("message"));
                    ship.placeMine();
                } else if (matchFire.matches()) {
                    int x = Integer.parseInt(matchFire.group("x"));
                    int y = Integer.parseInt(matchFire.group("y"));
                    ship.setMessage(matchFire.group("message"));
                    ship.fire(x, y);
                } else {
                    throw new InvalidInputException("A valid action", line);
                }
            }
        } catch (InvalidInputException e) {
            player.setDead();
            throw e;
        }
    }

    private void decrementRum() {
        for (Ship ship : ships) {
            ship.damage(1);
        }
    }

    private void updateInitialRum() {
        for (Ship ship : ships) {
            ship.initialHealth = ship.health;
        }
    }

    private void moveCannonballs() {
        for (Iterator<Cannonball> it = cannonballs.iterator(); it.hasNext();) {
            Cannonball ball = it.next();
            if (ball.remainingTurns == 0) {
                it.remove();
                continue;
            } else if (ball.remainingTurns > 0) {
                ball.remainingTurns--;
            }

            if (ball.remainingTurns == 0) {
                cannonBallExplosions.add(ball.position);
            }
        }
    }

    private void applyActions() {
        for (Player player : players) {
            for (Ship ship : player.shipsAlive) {
                if (ship.mineCooldown > 0) {
                    ship.mineCooldown--;
                }
                if (ship.cannonCooldown > 0) {
                    ship.cannonCooldown--;
                }

                ship.newOrientation = ship.orientation;

                if (ship.action != null) {
                    switch (ship.action) {
                    case FASTER:
                        if (ship.speed < MAX_SHIP_SPEED) {
                            ship.speed++;
                        }
                        break;
                    case SLOWER:
                        if (ship.speed > 0) {
                            ship.speed--;
                        }
                        break;
                    case PORT:
                        ship.newOrientation = (ship.orientation + 1) % 6;
                        break;
                    case STARBOARD:
                        ship.newOrientation = (ship.orientation + 5) % 6;
                        break;
                    case MINE:
                        if (ship.mineCooldown == 0) {
                            Coord target = ship.stern().neighbor((ship.orientation + 3) % 6);

                            if (target.isInsideMap()) {
                                boolean cellIsFreeOfBarrels = barrels.stream().noneMatch(barrel -> barrel.position.equals(target));
                                boolean cellIsFreeOfMines = mines.stream().noneMatch(mine -> mine.position.equals(target));
                                boolean cellIsFreeOfShips = ships.stream().filter(b -> b != ship).noneMatch(b -> b.at(target));

                                if (cellIsFreeOfBarrels && cellIsFreeOfShips && cellIsFreeOfMines) {
                                    ship.mineCooldown = COOLDOWN_MINE;
                                    Mine mine = new Mine(target.x, target.y);
                                    mines.add(mine);
                                }
                            }

                        }
                        break;
                    case FIRE:
                        int distance = ship.bow().distanceTo(ship.target);
                        if (ship.target.isInsideMap() && distance <= FIRE_DISTANCE_MAX && ship.cannonCooldown == 0) {
                            int travelTime = (int) (1 + Math.round(ship.bow().distanceTo(ship.target) / 3.0));
                            cannonballs.add(new Cannonball(ship.target.x, ship.target.y, ship.id, ship.bow().x, ship.bow().y, travelTime));
                            ship.cannonCooldown = COOLDOWN_CANNON;
                        }
                        break;
                    default:
                        break;
                    }
                }
            }
        }
    }

    private void checkCollisions(Ship ship) {
        Coord bow = ship.bow();
        Coord stern = ship.stern();
        Coord center = ship.position;

        // Collision with the barrels
        for (Iterator<RumBarrel> it = barrels.iterator(); it.hasNext();) {
            RumBarrel barrel = it.next();
            if (barrel.position.equals(bow) || barrel.position.equals(stern) || barrel.position.equals(center)) {
                ship.heal(barrel.health);
                it.remove();
            }
        }
		
        // Collision with the mines
        for (Iterator<Mine> it = mines.iterator(); it.hasNext();) {
            Mine mine = it.next();
            List<Damage> mineDamage = mine.explode(ships, false);

            if (!mineDamage.isEmpty()) {
                damage.addAll(mineDamage);
                it.remove();
            }
        }
    }

    private void moveShips() {
        // ---
        // Go forward
        // ---
        for (int i = 1; i <= MAX_SHIP_SPEED; i++) {
            for (Player player : players) {
                for (Ship ship : player.shipsAlive) {
                    ship.newPosition = ship.position;
                    ship.newBowCoordinate = ship.bow();
                    ship.newSternCoordinate = ship.stern();

                    if (i > ship.speed) {
                        continue;
                    }

                    Coord newCoordinate = ship.position.neighbor(ship.orientation);

                    if (newCoordinate.isInsideMap()) {
                        // Set new coordinate.
                        ship.newPosition = newCoordinate;
                        ship.newBowCoordinate = newCoordinate.neighbor(ship.orientation);
                        ship.newSternCoordinate = newCoordinate.neighbor((ship.orientation + 3) % 6);
                    } else {
                        // Stop ship!
                        ship.speed = 0;
                    }
                }
            }

            // Check ship and obstacles collisions
            List<Ship> collisions = new ArrayList<>();
            boolean collisionDetected = true;
            while (collisionDetected) {
                collisionDetected = false;

                for (Ship ship : this.ships) {
                    if (ship.newBowIntersect(ships)) {
                        collisions.add(ship);
                    }
                }

                for (Ship ship : collisions) {
                    // Revert last move
                    ship.newPosition = ship.position;
                    ship.newBowCoordinate = ship.bow();
                    ship.newSternCoordinate = ship.stern();

                    // Stop ships
                    ship.speed = 0;

                    collisionDetected = true;
                }
                collisions.clear();
            }

            // Move ships to their new location
            for (Ship ship : this.ships) {
            	ship.position = ship.newPosition;
            	}
            
            // Check collisions
            for (Ship ship : this.ships) {
            	checkCollisions(ship);
            	}
        }
    }

    private void rotateShips() {
        // Rotate
        for (Player player : players) {
            for (Ship ship : player.shipsAlive) {
                ship.newPosition = ship.position;
                ship.newBowCoordinate = ship.newBow();
                ship.newSternCoordinate = ship.newStern();
            }
        }

        // Check collisions
        boolean collisionDetected = true;
        List<Ship> collisions = new ArrayList<>();
        while (collisionDetected) {
            collisionDetected = false;

            for (Ship ship : this.ships) {
                if (ship.newPositionsIntersect(ships)) {
                    collisions.add(ship);
                }
            }

            for (Ship ship : collisions) {
                ship.newOrientation = ship.orientation;
                ship.newBowCoordinate = ship.newBow();
                ship.newSternCoordinate = ship.newStern();
                ship.speed = 0;
                collisionDetected = true;
            }

            collisions.clear();
        }

        // Apply rotation
        for (Ship ship : this.ships) {
        	ship.orientation = ship.newOrientation;
        }
	    // Check collisions
	    for (Ship ship : this.ships) {
	    	checkCollisions(ship);
	    }
    }

    private boolean gameIsOver() {
        for (Player player : players) {
            if (player.shipsAlive.isEmpty()) {
                return true;
            }
        }
        return barrels.size() == 0 && LEAGUE_LEVEL == 0;
    }

    void explodeShips() {
        for (Iterator<Coord> it = cannonBallExplosions.iterator(); it.hasNext();) {
            Coord position = it.next();
            for (Ship ship : ships) {
                if (position.equals(ship.bow()) || position.equals(ship.stern())) {
                    damage.add(new Damage(position, LOW_DAMAGE, true));
                    ship.damage(LOW_DAMAGE);
                    it.remove();
                    break;
                } else if (position.equals(ship.position)) {
                    damage.add(new Damage(position, HIGH_DAMAGE, true));
                    ship.damage(HIGH_DAMAGE);
                    it.remove();
                    break;
                }
            }
        }
    }

    void explodeMines() {
        for (Iterator<Coord> itBall = cannonBallExplosions.iterator(); itBall.hasNext();) {
            Coord position = itBall.next();
            for (Iterator<Mine> it = mines.iterator(); it.hasNext();) {
                Mine mine = it.next();
                if (mine.position.equals(position)) {
                    damage.addAll(mine.explode(ships, true));
                    it.remove();
                    itBall.remove();
                    break;
                }
            }
        }
    }

    void explodeBarrels() {
        for (Iterator<Coord> itBall = cannonBallExplosions.iterator(); itBall.hasNext();) {
            Coord position = itBall.next();
            for (Iterator<RumBarrel> it = barrels.iterator(); it.hasNext();) {
                RumBarrel barrel = it.next();
                if (barrel.position.equals(position)) {
                    damage.add(new Damage(position, 0, true));
                    it.remove();
                    itBall.remove();
                    break;
                }
            }
        }
    }

    protected void updateGame(int round) throws GameOverException {
        moveCannonballs();
        decrementRum();
        updateInitialRum();

        applyActions();
        moveShips();
        rotateShips();
		
        explodeShips();
        explodeMines();
        explodeBarrels();

        // For each sunk ship, create a new rum barrel with the amount of rum the ship had at the begin of the turn (up to 30).
        for (Ship ship : ships) {
            if (ship.health <= 0) {
                int reward = Math.min(REWARD_RUM_BARREL_VALUE, ship.initialHealth);
                if (reward > 0) {
                    barrels.add(new RumBarrel(ship.position.x, ship.position.y, reward));
                }
            }
        }

        for (Coord position : cannonBallExplosions) {
            damage.add(new Damage(position, 0, false));
        }

        for (Iterator<Ship> it = ships.iterator(); it.hasNext();) {
            Ship ship = it.next();
            if (ship.health <= 0) {
                players.get(ship.owner).shipsAlive.remove(ship);
                it.remove();
            }
        }

        if (gameIsOver()) {
            throw new GameOverException("endReached");
        }
    }


    protected void populateMessages(Properties p) {
        p.put("endReached", "End reached");
    }


    protected String[] getInitInputForPlayer(int playerIdx) {
        return new String[0];
    }


    protected String[] getInputForPlayer(int round, int playerIdx) {
        List<String> data = new ArrayList<>();

        // Player's ships first
        for (Ship ship : players.get(playerIdx).shipsAlive) {
            data.add(ship.toPlayerString(playerIdx));
        }

        // Number of ships
        data.add(0, String.valueOf(data.size()));

        // Opponent's ships
        for (Ship ship : players.get((playerIdx + 1) % 2).shipsAlive) {
            data.add(ship.toPlayerString(playerIdx));
        }

        // Visible mines
        for (Mine mine : mines) {
            boolean visible = false;
            for (Ship ship : players.get(playerIdx).shipsAlive) {
                if (ship.position.distanceTo(mine.position) <= MINE_VISIBILITY_RANGE) {
                    visible = true;
                    break;
                }
            }
            if (visible) {
                data.add(mine.toPlayerString(playerIdx));
            }
        }

        for (Cannonball ball : cannonballs) {
            data.add(ball.toPlayerString(playerIdx));
        }

        for (RumBarrel barrel : barrels) {
            data.add(barrel.toPlayerString(playerIdx));
        }

        data.add(1, String.valueOf(data.size() - 1));

        return data.toArray(new String[data.size()]);
    }


    protected String[] getInitDataForView() {
        List<String> data = new ArrayList<>();

        data.add(join(MAP_WIDTH, MAP_HEIGHT, players.get(0).ships.size(), MINE_VISIBILITY_RANGE));

        data.add(0, String.valueOf(data.size() + 1));

        return data.toArray(new String[data.size()]);
    }


    protected String[] getFrameDataForView(int round, int frame, boolean keyFrame) {
        List<String> data = new ArrayList<>();

        for (Player player : players) {
            data.addAll(player.toViewString());
        }
        data.add(String.valueOf(cannonballs.size()));
        for (Cannonball ball : cannonballs) {
            data.add(ball.toViewString());
        }
        data.add(String.valueOf(mines.size()));
        for (Mine mine : mines) {
            data.add(mine.toViewString());
        }
        data.add(String.valueOf(barrels.size()));
        for (RumBarrel barrel : barrels) {
            data.add(barrel.toViewString());
        }
        data.add(String.valueOf(damage.size()));
        for (Damage d : damage) {
            data.add(d.toViewString());
        }

        return data.toArray(new String[data.size()]);
    }


    protected String getGameName() {
        return "CodersOfTheCaribbean";
    }


    protected String getHeadlineAtGameStartForConsole() {
        return null;
    }


    protected int getMinimumPlayerCount() {
        return 2;
    }


    protected boolean showTooltips() {
        return true;
    }


    protected String[] getPlayerActions(int playerIdx, int round) {
        return new String[0];
    }


    protected boolean isPlayerDead(int playerIdx) {
        return false;
    }


    protected String getDeathReason(int playerIdx) {
        return "$" + playerIdx + ": Eliminated!";
    }


    protected int getScore(int playerIdx) {
        return players.get(playerIdx).getScore();
    }

    protected int eval(int idPlayer) {
    	int idOpponentPlayer = (idPlayer == 1) ? 0 : 1;
    	int barrels = 0; int teamUp = 0; int hunt = 0;
    	if (this.barrels.size() != 0)
    	{
        	for (Ship ship : players.get(idPlayer).shipsAlive)
        	{
        		RumBarrel rum = ship.closestBarrel(this.barrels);
        		int distance = ship.bow().distanceTo(rum.position);
        		barrels += 12 / distance;
        		if (distance == 1 ) barrels = 0;
        	}
    	}
    	if (1 < players.get(idPlayer).shipsAlive.size())
    	{
        	for (Ship ship1 : players.get(idPlayer).shipsAlive)
        	{
            	for (Ship ship2 : players.get(idPlayer).shipsAlive)
            	{
            		if (ship1.id == ship2.id) continue;
            		int distance = ship1.position.distanceTo(ship2.position);
            		if (distance <= 1) teamUp -= 1 / distance;
            		else if (distance <= 3) teamUp +=  3 / distance;
            	}
        	}
    	}
    	Ship mainTarget = null; int maxRum = 0;
    	if (1 < players.get(idOpponentPlayer).shipsAlive.size() && this.barrels.size() == 0)
    	{
    		for (Ship opponentShip : players.get(idOpponentPlayer).shipsAlive)
    		{
    			if (maxRum < opponentShip.health)
    			{
    				mainTarget = opponentShip;
    				maxRum = opponentShip.health;
    			}
    		}
       		for (Ship ship : players.get(idPlayer).shipsAlive)
    		{
       			int distance = ship.bow().distanceTo(mainTarget.position);
       			if (distance < 4 && ship.speed != 0) hunt += 2 / distance;
    		}
    	}
    	int shotDone = 0;
    	
    	System.err.println("barrels: " + barrels);
        return hunt + shotDone + teamUp + barrels + players.get(idPlayer).getScore() - players.get(idOpponentPlayer).getScore();
    }

    protected String[] getGameSummary(int round) {
        return new String[0];
    }


    protected void setPlayerTimeout(int frame, int round, int playerIdx) {
        players.get(playerIdx).setDead();
    }


    protected int getMaxRoundCount(int playerCount) {
        return 200;
    }


    protected int getMillisTimeForRound() {
        return 50;
    }
    
    protected void constructShip(int entityId, String entityType, int x, int y, int arg1, int arg2, int arg3, int arg4)
    {
    	if (entityType.toUpperCase().equals("SHIP"))
    	{
    		Ship ship = new Ship(entityId, x, y, arg1, arg3, arg4);
            this.ships.add(ship);
            this.players.get(arg4).ships.add(ship);
            this.players.get(arg4).shipsAlive.add(ship);
    	}
    }
    
    protected void displayEntities()
    {
        System.err.println("ShipsAlive");
        for (Player player : this.players)
        {
    	    System.err.println("Player: " + player.id);
            for (Ship shipAlive : player.shipsAlive)
            {
                System.err.println(shipAlive.toViewString());
            }
            
        }
        /*
        System.err.println("Barrels");
        for (RumBarrel barrel : this.barrels)
        {
            System.err.println(barrel.toViewString());
        }
        
        System.err.println("Cannonball");
        for (Cannonball cannonball : cannonballs)
        {
            System.err.println(cannonball.toViewString());
        }
        
        System.err.println("Mines");
        for (Mine mine : this.mines)
        {
            System.err.println(mine.toViewString());
        }
        */
    }
	
	protected void checkShipRemoved(List<String> ships)
	{
		if (this.ships.size() != 0 && ships.size() != 0)
		{
			Iterator<Ship> iterator = this.ships.iterator(); 
			while (iterator.hasNext())
			{
				Ship ship = iterator.next();
				boolean foundElement = false;
				for (String shipInput : ships)
				{
					int entityId = Integer.parseInt(shipInput.split(";")[0]);
					if (ship.getId() == entityId) 
					{
						foundElement = true; 
						break;
					}
				}
				if (foundElement == false)
				{
					iterator.remove();
				}
			}
		}
	}
    
    protected void updateEntity(int entityId, String entityType, int x, int y, int arg1, int arg2, int arg3, int arg4) throws InvalidFormatException 
    {
        if (entityType.toUpperCase().equals("SHIP"))
        {
        	boolean foundElement = false;
            for (Ship ship : this.players.get(arg4).shipsAlive)
            {
                if (ship.getId() == entityId)
                {
                    ship.update(entityId, x, y, arg1, arg2, arg3, arg4);
                    foundElement = true;
                    break;
                }
            }
            if (foundElement == false)
            {
            	this.constructShip(entityId, entityType, x, y, arg1, arg2, arg3, arg4);
            }
        }
        else if (entityType.toUpperCase().equals("BARREL"))
        {
            boolean foundElement = false;
            for (RumBarrel barrel : this.barrels)
            {
                if (barrel.position.x == x && barrel.position.y == y)
                {
                    foundElement = true;
                    break;
                }
            }
            if (foundElement == false)
            {
				RumBarrel barrel = new RumBarrel(x, y, arg1);
                this.barrels.add(barrel);
            }
        }
        else if (entityType.toUpperCase().equals("CANNONBALL"))
        {
			boolean foundElement = false;
			for (Cannonball cannonball : cannonballs)
			{
				if (cannonball.position.x == x && cannonball.position.y == y && cannonball.remainingTurns== arg2 &&  cannonball.ownerEntityId == arg1) 
				{
					foundElement = true;
					break;
				}
			}
			if (foundElement == false)
			{
			    for (Ship srcShip : this.ships)
				{
					if (srcShip.id == arg1)
					{
						int srcX = srcShip.position.x;
						int srcY = srcShip.position.y;
						Cannonball cannonball = new Cannonball(x, y, arg1, srcX, srcY, arg2);
						this.cannonballs.add(cannonball);
						break;
					}
				}	
			}
        }
        else if (entityType.toUpperCase().equals("MINE"))
        {
            boolean foundElement = false;
            for (Mine mine : this.mines)
            {
                if (mine.position.x == x && mine.position.y == y)
                {
                    foundElement = true;
                    break;
                }
            }
            if (foundElement == false)
            {
				Mine m = new Mine(x, y);
                this.mines.add(m);
            }
        } 
    }
    
    // One turn careful need saving and loading
    protected void apply(int idPlayer, String[] outputs) throws WinException, LostException, InvalidInputException, GameOverException
    {
		this.prepare(1);
		this.handlePlayerOutput(1, 1, idPlayer, outputs);
		this.updateGame(1);
    }

	public static class Solution implements Serializable
	{
		int shipCount;
		List<String> shipMoves1;
		List<String> shipMoves2;
		List<String> shipMoves3;
		Referee referee;
		Integer[] score;
		int sumScore;
		int depth;
		int idPlayer;
		int idOpponentPlayer;
	    
	    public Solution(Referee referee, int playerId)
	    {
	    	this.depth = 4;
	    	this.shipMoves1 = new ArrayList<String>();
	    	this.shipMoves2 = new ArrayList<String>();
	    	this.shipMoves3 = new ArrayList<String>();
	    	this.shipCount = referee.players.get(playerId).shipsAlive.size();
	    	this.score = new Integer[depth];
	    	this.sumScore = 0;
            this.referee = referee;
            this.idPlayer = 1;
            this.idOpponentPlayer = (idPlayer == 1) ? 0 : 1;
	    }

        public List<Coord> computeFireTargets(Ship ship)
        {
        	List<Coord> fireTargets = new ArrayList<Coord>();
        	for (Ship opponentShip : referee.players.get(idOpponentPlayer).shipsAlive)
        	{
        		int distance = ship.bow().distanceTo(opponentShip.position);
                int travelTime = (int) (1 + Math.round(distance / 3.0));
        		if (travelTime <= 1)
        		{
                    fireTargets.add(new Coord(opponentShip.position.x , opponentShip.position.y));
                    return fireTargets;
        		}
        		else if (distance < FIRE_DISTANCE_MAX / 2)
        		{
        			int newX; int newY;
                    if (opponentShip.position.y % 2 == 1) {
                        newY = opponentShip.position.y + opponentShip.position.DIRECTIONS_ODD[opponentShip.orientation][1] * opponentShip.speed * travelTime;
                        newX = opponentShip.position.x + opponentShip.position.DIRECTIONS_ODD[opponentShip.orientation][0] * opponentShip.speed * travelTime;
                    } else {
                        newY = opponentShip.position.y + opponentShip.position.DIRECTIONS_EVEN[opponentShip.orientation][1] * opponentShip.speed * travelTime;
                        newX = opponentShip.position.x + opponentShip.position.DIRECTIONS_EVEN[opponentShip.orientation][0] * opponentShip.speed * travelTime;
                    }
                    
                    fireTargets.add(new Coord(newX, newY));
        		}
        	}
        	if (fireTargets.size() == 0 && referee.mines.size() != 0)
        	{
        		Mine closestMine = ship.closestMine(this.referee.mines);
        		int distance = ship.bow().distanceTo(closestMine.position);
        		if (1 < distance && distance <= FIRE_DISTANCE_MAX)
        		{
                    fireTargets.add(new Coord(closestMine.position.x, closestMine.position.y));
        		}
        	}
        	return fireTargets;
        }
        

	    protected String[] makeDecision(Referee referee)
	    {
        	int iShip = 0;
        	String[] move = new String[referee.players.get(idPlayer).shipsAlive.size()];
	    	for (Ship ship : referee.players.get(idPlayer).shipsAlive)
            {
    			String currentMove = "WAIT";
    			if (referee.barrels.size() != 0)
            	{
                    RumBarrel rum = ship.closestBarrel(referee.barrels);
                    currentMove = "MOVE " + rum.position.x + " " + rum.position.y;
            	}
    			if (ship.cannonCooldown == 0)
            	{
    				List<Coord> fireTargets = this.computeFireTargets(ship);
    				if (fireTargets.size() != 0) currentMove = "FIRE " + fireTargets.get(0).x + " " + fireTargets.get(0).y;
            	}
                move[iShip] = currentMove;
                iShip++;
            }
            return move;
	    }
	    
	    protected void heuristicSimulation(int depth, Referee referee)
	    {
	    	int idPlayer = 1;
	    	for (int iDepth = 0; iDepth < depth; iDepth++)
	    	{
	    		try
	    		{
		    		String[] outputTurn = this.makeDecision(referee);
	                referee.apply(idPlayer, outputTurn);

		            this.score[iDepth] = referee.eval(idPlayer);
		            this.sumScore += this.score[iDepth];
		            
	                int iShip = 0;
	                if (1 <= shipCount)
	                {
	                	shipMoves1.add(outputTurn[iShip]);
	                	iShip++;
	                }
	                if (2 <= shipCount)
	                {
	                	shipMoves2.add(outputTurn[iShip]);
	                	iShip++;
	                }
	                if (3 <= shipCount)
	                {
	                	shipMoves3.add(outputTurn[iShip]);
	                	iShip++;
	                }
	    		}
	    		catch (Exception e)
	    		{
	    			System.err.println(e);
	    		}
	    	}
	    }
	    
	    public void mutate()
	    {
    	    for (int i = 0; i < depth; i++)
    	    {
    	    	int amplitude = 1 / (i + 1);
    	    	
                if (referee.random.nextInt() < amplitude && 1 <= this.referee.players.get(idPlayer).shipsAlive.size())
                {
                	shipMoves1.set(i, randomAction(this.referee.players.get(idPlayer).shipsAlive.get(0)));
                }
                if (referee.random.nextInt() < amplitude && 2 <= this.referee.players.get(idPlayer).shipsAlive.size())
                {
                	shipMoves2.set(i, randomAction(this.referee.players.get(idPlayer).shipsAlive.get(1)));
                }
                if (referee.random.nextInt() < amplitude && 3 <= this.referee.players.get(idPlayer).shipsAlive.size())
                {
                	shipMoves3.set(i, randomAction(this.referee.players.get(idPlayer).shipsAlive.get(2)));
                }
    	    }
	    }
	    public void randomize()
	    {
    	    for (int i = 0; i < this.depth; i++)
    	    {
                if (1 <=  this.referee.players.get(idPlayer).shipsAlive.size())
                {
                	shipMoves1.add(i, randomAction(this.referee.players.get(idPlayer).shipsAlive.get(0)));
                }
                if (2 <=  this.referee.players.get(idPlayer).shipsAlive.size())
                {
                	shipMoves2.add(i, randomAction(this.referee.players.get(idPlayer).shipsAlive.get(1)));
                }
                if (3 <=  this.referee.players.get(idPlayer).shipsAlive.size())
                {
                	shipMoves3.add(i, randomAction(this.referee.players.get(idPlayer).shipsAlive.get(2)));
                }
    	    }
	    }

	    public String randomAction(Ship ship)
	    {
	    	int countCommand;
	    	if (LEAGUE_LEVEL == 0) countCommand = 0;
	    	else if (LEAGUE_LEVEL == 1 || LEAGUE_LEVEL == 2) countCommand = 2;
	    	else countCommand = 6;
	    	
	    	String move = "";
	    	while (move == "")
	    	{
		    	int command = referee.random.nextInt(countCommand);
		    	switch(command)
		        {
			        case 0: move = "WAIT"; break;
			        case 1: {
			        	if (ship.cannonCooldown == 0)
			        	{
				        	List<Coord> fireTargets = new ArrayList<Coord>();
				        	fireTargets = this.computeFireTargets(ship);
				        	if (fireTargets.size() != 0)
				        	{
				        		int idFireTarget = referee.random.nextInt(fireTargets.size());
				        		move = "FIRE " + fireTargets.get(idFireTarget).x + " " + fireTargets.get(idFireTarget).y;
				        	}
			        	}

			        }
			        break;
			    	case 2: move = "SLOWER"; break;
			        case 3: move = "FASTER"; break;
			        case 4: move = "PORT"; break;
			        case 5: move = "STARBOARD"; break;
		        }	
	    	}
	    	return move;
	    }
	    
	    public void eval(int idPlayer)
	    {
	    	this.sumScore = 0;
    		for (int i = 0; i < this.depth; i++)
    		{
    			int j = 0;
        		String[] outputs = new String[referee.players.get(idPlayer).shipsAlive.size()];
        		
                if (1 <= referee.players.get(idPlayer).shipsAlive.size())
                {
                	outputs[j] = this.shipMoves1.get(i);
                	j++;
                }
                if (2 <= referee.players.get(idPlayer).shipsAlive.size())
                {
                	outputs[j] = this.shipMoves2.get(i);
                	j++;
                }
                if (3 <= referee.players.get(idPlayer).shipsAlive.size())
                {
                	outputs[j] = this.shipMoves3.get(i);
                	j++;
                }
    	    	try {
					referee.apply(idPlayer, outputs);
				} catch (WinException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (LostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvalidInputException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (GameOverException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    	    	this.score[i] = referee.eval(idPlayer);
    	    	this.sumScore += this.score[i];
    		}
	    }
	    
	    public List<String> rotateLeft ( List<String> tab ) 
	    {
	    	if (2 <= tab.size())
	    	{
		        List<String> tabrot = new ArrayList<String>();
		        String firstElement = tab.get(0);
		        for (int i = 0 ; i < tab.size() - 1; i++)
		        {
		            tabrot.add(i, tab.get(i+1));
		        }
		        tabrot.add(tab.size() - 1, firstElement);
		        return tabrot;
	    	}
	    	else return tab;
	    }
	    
	    public String[] outputMove(int idPlayer)
	    {
	    	String[] outputTurn = new String[this.referee.players.get(idPlayer).shipsAlive.size()];
	    	for (int iShipCount = 0; iShipCount < this.referee.players.get(idPlayer).shipsAlive.size(); iShipCount++)
	    	{
	    		try
	    		{
		    		if (iShipCount == 0)
		            {
		            	outputTurn[iShipCount] = shipMoves1.get(0);
		            	shipMoves1 = rotateLeft(shipMoves1);
		            	System.out.println(outputTurn[iShipCount]);
		            }
		    		else if (iShipCount == 1)
		            {
		            	outputTurn[iShipCount] = shipMoves2.get(0);
		            	shipMoves2 = rotateLeft(shipMoves2);
		            	System.out.println(outputTurn[iShipCount]);
		            }
		    		else if (iShipCount == 2)
		            {
		            	outputTurn[iShipCount] = shipMoves3.get(0);
		            	shipMoves3 = rotateLeft(shipMoves3);
		            	System.out.println(outputTurn[iShipCount]);
		            }
	    		}
	    		catch (Exception e) // Gameover
	    		{
	    			outputTurn[iShipCount] = "WAIT GOING TO DIE";
	            	System.out.println("WAIT GOING TO DIE");
	    		}
	    	}

	    	return outputTurn;
	    }
	    
	    public String concatenate()
	    {
	    	String concatenate = "";

			if (1 <= this.shipCount)
			{
				for (int iDepth = 0; iDepth < this.shipMoves1.size() ; iDepth++)
				{
					concatenate += this.shipMoves1.get(iDepth) + ";";
				}
			}
			if (2 <= this.shipCount)
			{
				for (int iDepth = 0; iDepth < this.shipMoves2.size() ; iDepth++)
				{
					concatenate += this.shipMoves2.get(iDepth) + ";";
				}
			}
			if (3 <= this.shipCount)
			{				
				for (int iDepth = 0; iDepth < this.shipMoves3.size() ; iDepth++)
				{
					concatenate += this.shipMoves3.get(iDepth) + ";";
				}
			}
			return concatenate;
	    }
	    
	    public void display()
	    {
    	    System.err.println("Printing moves 1");
    	    for (String val : this.shipMoves1)
    	    {
    	    	System.err.println(val);
    	    }
    	    System.err.println("Printing moves 2");
    	    for (String val : this.shipMoves2)
    	    {
    	    	System.err.println(val);
    	    }
    	    System.err.println("Printing moves 3");
    	    for (String val : this.shipMoves3)
    	    {
    	    	System.err.println(val);
    	    }
    	    System.err.println("sumScore: " + this.sumScore);
	    }
	}
}


class Player implements Serializable {

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        boolean DEBUG_MODE = false;
        // Initialization of our simulation
        InputStream is = System.in;
        PrintStream out= System.out;
        PrintStream err= System.err;
       	try {
			Referee currentBoard = new Referee(is, out, err);
			// Initialize a random game
            Properties prop = currentBoard.getConfiguration();
            currentBoard.initEmptyReferee(2, prop);
            int gameTurn = 1;
            int idPlayer = 1;
            int depth = 4;
            
            // game loop
			int saveCountShips = 99; // save the last number of ships
			
            Referee.Solution bestSolution = null;
            while (gameTurn < 401) {
                long startTime = System.currentTimeMillis();
                // Update new inputs
                int myShipCount = in.nextInt(); // the number of remaining ships
                int entityCount = in.nextInt(); // the number of entities (e.g. ships, mines or cannonballs)
    			List<String> ships = new ArrayList(); // (remove opponent ships that is destroyed because of unknown mines)
                for (int i = 0; i < entityCount; i++) {
                    int entityId = in.nextInt();
                    String entityType = in.next();
                    int x = in.nextInt();
                    int y = in.nextInt();
                    int arg1 = in.nextInt();
                    int arg2 = in.nextInt();
                    int arg3 = in.nextInt();
                    int arg4 = in.nextInt();
					if (entityType.equals("SHIP")) // opponent ship
					{
						ships.add(entityId + ";" + entityType + ";" + x + ";" + y + ";" + arg1 + ";" + arg2 + ";" + arg3 + ";" + arg4);
					}
                    // Create + Update current entity
                    currentBoard.updateEntity(entityId, entityType, x, y, arg1, arg2, arg3, arg4);
                }
				
				if (saveCountShips != ships.size()) { // ship lost
					// check if the simulation removed the ship, it can explode with mines that were never seen
					currentBoard.checkShipRemoved(ships);
					saveCountShips = ships.size();
				}
				/*
				System.err.println("After update vars");
				currentBoard.displayEntities();
				*/
				
				// Simulation
				try {

					Referee saveBoard1 = (Referee)(ObjectCloner.deepCopy(currentBoard));
					if (gameTurn == 1)
					{
						bestSolution = new Referee.Solution(saveBoard1, idPlayer);
						bestSolution.randomize();
					}
					else bestSolution.referee = saveBoard1;
					// bestSolution.heuristicSimulation(bestSolution.depth, saveBoard1);
					bestSolution.eval(idPlayer);
					bestSolution.referee = currentBoard;
				}
				catch (Exception e)
				{
					System.err.println("Error heuristic: " + e);
				}

				// mutate process
				List<String> outputTried = new ArrayList<String>();
                long endTime = System.currentTimeMillis();;
                while ((endTime - startTime) < 950 && gameTurn == 1 || gameTurn != 1 && (endTime - startTime) < 40)
	            {
                	Referee saveBoard2 = null; Referee.Solution solution = null;
					try {
						// mutate
						saveBoard2 = (Referee) ObjectCloner.deepCopy(currentBoard);
						solution = (Referee.Solution) ObjectCloner.deepCopy(bestSolution);
					}
					catch (Exception e)
					{
						System.err.println("Error copy mutate: " + e);
					}
					String concatenate = "";
					do
					{
						if ((endTime - startTime) > 950 && gameTurn == 1 || gameTurn != 1 && (endTime - startTime) > 40) break;
						solution.mutate();
						concatenate = solution.concatenate();
						endTime = System.currentTimeMillis();
					} while (outputTried.contains(concatenate));
					if ((endTime - startTime) > 950 && gameTurn == 1 || gameTurn != 1 && (endTime - startTime) > 40) break;
					if (!outputTried.contains(concatenate))
					{
						try
						{
							solution.eval(idPlayer);
							if (bestSolution.sumScore < solution.sumScore)
							{
								bestSolution = solution;
								bestSolution.referee = currentBoard;
							}
						}
						catch (Exception e)
						{
							System.err.println("Error eval mutate apply: " + e);
						}

						outputTried.add(concatenate);
					}
					endTime = System.currentTimeMillis();
	            }
	           
	            //bestSolution.display();
	            
				// output bestSolution
				String[] outputTurn = bestSolution.outputMove(idPlayer);
				
		    	try
		    	{
			    	currentBoard.apply(idPlayer, outputTurn);
		    	}
		    	catch (Exception e)
		    	{
		    		System.err.println(e);
		    	}
		    	/*
				System.err.println("After update game");
				currentBoard.displayEntities();
				*/
            	gameTurn++;
            }
		} 
       	catch (IOException e)
       	{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
       	catch (InvalidFormatException  e)
       	{
       		e.printStackTrace();
       	}
    }
}