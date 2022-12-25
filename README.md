## Deployment

### 1. Ions

Adapted from https://docs.datomic.com/cloud/ions-tutorial/push-and-deploy.html

using https://github.com/borkdude/jet

```shell
# 1. make sure git status is clean

# 2. then do:
deploy_command=$(clojure -A:ion-dev '{:op :push}' | jet --query ':deploy-command println' | grep -v -x -F 'nil')

# 3. then use deploy command from push result, similar to this:
status_command=$(eval "$deploy_command" | jet --query ':status-command println')

# 4. monitor status with command from deploy result, similar to this:
eval "$status_command"
```

### 2. App Sync Api

```shell
cdk deploy
```