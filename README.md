## Deployment

### 1. Ions

Adapted from https://docs.datomic.com/cloud/ions-tutorial/push-and-deploy.html

using https://github.com/borkdude/jet

```shell
# 1. make sure git status is clean

# 2. then do:
push_result=$(clojure -A:ion-dev '{:op :push}')
echo "$push_result"
deploy_command=$(echo "$push_result" | jet --query ':deploy-command println' | grep -v -x -F 'nil')

# 3. then use deploy command from push result, similar to this:
deploy_result=$(eval "$deploy_command")
echo "$deploy_result"
status_command=$(echo "$deploy_result" | jet --query ':status-command println')

# 4. monitor status with command from deploy result, similar to this:
while
  state=$(eval "$status_command")
  echo "$state" | jet --query ':code-deploy-status println' | grep -x -F 'RUNNING'
do
  echo "running: $state"
  sleep 1
done
echo "finished: $state"
```

### 2. App Sync Api

```shell
cdk deploy
```