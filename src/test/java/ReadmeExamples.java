import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import org.dataloader.BatchLoader;
import org.dataloader.CacheMap;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.DataLoaderRegistry;
import org.dataloader.Try;
import org.dataloader.fixtures.User;
import org.dataloader.fixtures.UserManager;
import org.dataloader.graphql.DataLoaderDispatcherInstrumentation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@SuppressWarnings("ALL")
public class ReadmeExamples {


    UserManager userManager = new UserManager();

    public static void main(String[] args) {
        ReadmeExamples examples = new ReadmeExamples();
        examples.basicExample();
    }

    @SuppressWarnings({"Convert2Lambda", "Convert2MethodRef", "CodeBlock2Expr"})
    void basicExample() {

        BatchLoader<Long, User> lessEfficientUserBatchLoader = new BatchLoader<Long, User>() {
            @Override
            public CompletionStage<List<User>> load(List<Long> userIds) {
                return CompletableFuture.supplyAsync(() -> {
                    //
                    // notice how it makes N calls to load by single user id out of the batch of N keys
                    //
                    return userIds.stream()
                            .map(id -> userManager.loadUserById(id))
                            .collect(Collectors.toList());
                });
            }
        };

        BatchLoader<Long, User> userBatchLoader = new BatchLoader<Long, User>() {
            @Override
            public CompletionStage<List<User>> load(List<Long> userIds) {
                return CompletableFuture.supplyAsync(() -> {
                    return userManager.loadUsersById(userIds);
                });
            }
        };

        DataLoader<Long, User> userLoader = new DataLoader<>(userBatchLoader);

        CompletionStage<User> load1 = userLoader.load(1L);

        userLoader.load(1L)
                .thenAccept(user -> {
                    System.out.println("user = " + user);
                    userLoader.load(user.getInvitedByID())
                            .thenAccept(invitedBy -> {
                                System.out.println("invitedBy = " + invitedBy);
                            });
                });

        userLoader.load(2L)
                .thenAccept(user -> {
                    System.out.println("user = " + user);
                    userLoader.load(user.getInvitedByID())
                            .thenAccept(invitedBy -> {
                                System.out.println("invitedBy = " + invitedBy);
                            });
                });

        userLoader.dispatchAndJoin();
    }


    class StarWarsCharacter {
        List<String> getFriendIds() {
            return null;
        }
    }

    void starWarsExample() {

        // a batch loader function that will be called with N or more keys for batch loading
        BatchLoader<String, Object> characterBatchLoader = new BatchLoader<String, Object>() {
            @Override
            public CompletionStage<List<Object>> load(List<String> keys) {
                //
                // we use supplyAsync() of values here for maximum parellisation
                //
                return CompletableFuture.supplyAsync(() -> getCharacterDataViaBatchHTTPApi(keys));
            }
        };

        // a data loader for characters that points to the character batch loader
        DataLoader characterDataLoader = new DataLoader<String, Object>(characterBatchLoader);

        //
        // use this data loader in the data fetchers associated with characters and put them into
        // the graphql schema (not shown)
        //
        DataFetcher heroDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                return characterDataLoader.load("2001"); // R2D2
            }
        };

        DataFetcher friendsDataFetcher = new DataFetcher() {
            @Override
            public Object get(DataFetchingEnvironment environment) {
                StarWarsCharacter starWarsCharacter = environment.getSource();
                List<String> friendIds = starWarsCharacter.getFriendIds();
                return characterDataLoader.loadMany(friendIds);
            }
        };

        //
        // DataLoaderRegistry is a place to register all data loaders in that needs to be dispatched together
        // in this case there is 1 but you can have many
        //
        DataLoaderRegistry registry = new DataLoaderRegistry();
        registry.register("character", characterDataLoader);

        //
        // this instrumentation implementation will dispatched all the dataloaders
        // as each level fo the graphql query is executed and hence make batched objects
        // available to the query and the associated DataFetchers
        //
        DataLoaderDispatcherInstrumentation dispatcherInstrumentation
                = new DataLoaderDispatcherInstrumentation(registry);

        //
        // now build your graphql object and execute queries on it.
        // the data loader will be invoked via the data fetchers on the
        // schema fields
        //
        GraphQL graphQL = GraphQL.newGraphQL(buildSchema())
                .instrumentation(dispatcherInstrumentation)
                .build();

    }

    private GraphQLSchema buildSchema() {
        return null;
    }

    private List<Object> getCharacterDataViaBatchHTTPApi(List<String> keys) {
        return null;
    }


    private void tryExample() {
        Try<String> tryS = Try.tryCall(() -> {
            if (rollDice()) {
                return "OK";
            } else {
                throw new RuntimeException("Bang");
            }
        });

        if (tryS.isSuccess()) {
            System.out.println("It work " + tryS.get());
        } else {
            System.out.println("It failed with exception :  " + tryS.getThrowable());

        }
    }

    private void tryBatcLoader() {
        DataLoader<String, User> dataLoader = DataLoader.newDataLoaderWithTry(new BatchLoader<String, Try<User>>() {
            @Override
            public CompletionStage<List<Try<User>>> load(List<String> keys) {
                return CompletableFuture.supplyAsync(() -> {
                    List<Try<User>> users = new ArrayList<>();
                    for (String key : keys) {
                        Try<User> userTry = loadUser(key);
                        users.add(userTry);
                    }
                    return users;
                });
            }
        });
    }

    DataLoader<String, User> userDataLoader;

    private void clearCacheOnError() {

        userDataLoader.load("r2d2").whenComplete((user, throwable) -> {
            if (throwable != null) {
                userDataLoader.clear("r2dr");
                throwable.printStackTrace();
            } else {
                processUser(user);
            }
        });
    }

    BatchLoader<String, User> userBatchLoader;

    private void disableCache() {
        new DataLoader<String, User>(userBatchLoader, DataLoaderOptions.newOptions().setCachingEnabled(false));


        userDataLoader.load("A");
        userDataLoader.load("B");
        userDataLoader.load("A");

        userDataLoader.dispatch();

        // will result in keys to the batch loader with [ "A", "B", "A" ]
    }

    class MyCustomCache implements CacheMap {
        @Override
        public boolean containsKey(Object key) {
            return false;
        }

        @Override
        public Object get(Object key) {
            return null;
        }

        @Override
        public CacheMap set(Object key, Object value) {
            return null;
        }

        @Override
        public CacheMap delete(Object key) {
            return null;
        }

        @Override
        public CacheMap clear() {
            return null;
        }
    }

    private void customCache() {

        MyCustomCache customCache = new MyCustomCache();
        DataLoaderOptions options = DataLoaderOptions.newOptions().setCacheMap(customCache);
        new DataLoader<String, User>(userBatchLoader, options);
    }

    private void processUser(User user) {

    }

    private Try<User> loadUser(String key) {
        return null;
    }

    private boolean rollDice() {
        return false;
    }


}
